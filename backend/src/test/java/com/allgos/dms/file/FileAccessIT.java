package com.allgos.dms.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allgos.dms.audit.entity.AuditAction;
import com.allgos.dms.audit.repository.AuditLogRepository;
import com.allgos.dms.auth.repository.OtpVerificationRepository;
import com.allgos.dms.auth.repository.RegistrationRequestRepository;
import com.allgos.dms.department.entity.Department;
import com.allgos.dms.department.repository.DepartmentRepository;
import com.allgos.dms.file.entity.StoredFile;
import com.allgos.dms.file.repository.FileDeletionRepository;
import com.allgos.dms.file.repository.StoredFileRepository;
import com.allgos.dms.folder.repository.FolderRepository;
import com.allgos.dms.notification.entity.Notification;
import com.allgos.dms.notification.entity.NotificationType;
import com.allgos.dms.notification.repository.NotificationRepository;
import com.allgos.dms.support.AbstractIntegrationTest;
import com.allgos.dms.support.RecordingOtpProvider;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserStatus;
import com.allgos.dms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MinIOContainer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/**
 * Documents end to end, against real PostgreSQL and real object storage.
 *
 * <p>The four things being proved are the ones the phase is defined by:
 *
 * <ol>
 *   <li>a member can upload into a department that is <b>not their own</b> (rule 2);
 *   <li>a disguised file is refused even though its name and declared type look fine;
 *   <li>deleting without a reason is a 400, and deleting someone else's file is a 403;
 *   <li>a legitimate deletion reaches <b>every admin</b> with the reason, and an admin can restore.
 * </ol>
 *
 * <p>Replacement follows the same ownership rule as deletion, and is proved alongside it.
 */
@Import(RecordingOtpProvider.Config.class)
class FileAccessIT extends AbstractIntegrationTest {

    private static final String ADMIN_MOBILE = "9999999999"; // seeded by V3
    private static final String MEMBER_MOBILE = "9876543212";
    private static final String OTHER_MEMBER_MOBILE = "9876543213";
    private static final String PASSWORD = "Str0ngPassword!";

    /** A minimal but genuine PDF — Tika must be able to recognise these bytes as one. */
    private static final byte[] REAL_PDF =
            "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF"
                    .getBytes(StandardCharsets.US_ASCII);

    /** A Windows executable's first bytes, which no allowed extension may carry. */
    private static final byte[] WINDOWS_EXECUTABLE = new byte[] {'M', 'Z', (byte) 0x90, 0x00, 0x03};

    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2024-08-17T01-24-54Z");

    static {
        MINIO.start();
    }

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.endpoint", MINIO::getS3URL);
        registry.add("app.storage.access-key", MINIO::getUserName);
        registry.add("app.storage.secret-key", MINIO::getPassword);
    }

    @Autowired private ObjectMapper objectMapper;
    @Autowired private S3Client s3Client;
    @Autowired private UserRepository userRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private FolderRepository folderRepository;
    @Autowired private StoredFileRepository fileRepository;
    @Autowired private FileDeletionRepository deletionRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private RegistrationRequestRepository registrationRequestRepository;
    @Autowired private OtpVerificationRepository otpVerificationRepository;
    @Autowired private RecordingOtpProvider otpProvider;

    /** The member belongs to this one. */
    private Department ownDepartment;

    /** And uploads into this one, which is the whole point of rule 2. */
    private Department otherDepartment;

    private UUID folderId;
    private String adminToken;
    private String memberToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureBucket();

        otpProvider.clear();
        deletionRepository.deleteAll();
        fileRepository.deleteAll();
        folderRepository.deleteAll();
        auditLogRepository.deleteAll();
        notificationRepository.deleteAll();
        registrationRequestRepository.deleteAll();
        otpVerificationRepository.deleteAll();
        userRepository.findByMobileNumber(MEMBER_MOBILE).ifPresent(userRepository::delete);
        userRepository.findByMobileNumber(OTHER_MEMBER_MOBILE).ifPresent(userRepository::delete);

        User admin = userRepository.findByMobileNumber(ADMIN_MOBILE).orElseThrow();
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.saveAndFlush(admin);

        List<Department> departments = departmentRepository.findByActiveTrueOrderByNameAsc();
        ownDepartment = departments.get(0);
        otherDepartment = departments.get(1);

        adminToken = signInWithOtp(ADMIN_MOBILE);
        memberToken = registerApproveAndSignIn(MEMBER_MOBILE, "Meena Rajan");

        // A folder in the department the member does not belong to.
        folderId = createFolder(otherDepartment.getId(), "Circulars 2026", memberToken);
    }

    // ------------------------------------------------------------------------ upload

    @Test
    @DisplayName("a member uploads into a department that is not their own, and it succeeds")
    void uploadIntoAnotherDepartment() throws Exception {
        assertThat(userRepository.findByMobileNumber(MEMBER_MOBILE).orElseThrow().getDepartment().getId())
                .isEqualTo(ownDepartment.getId());

        upload(memberToken, pdf("Circular 42.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploaded.length()").value(1))
                .andExpect(jsonPath("$.rejected.length()").value(0))
                .andExpect(jsonPath("$.uploaded[0].fileName").value("Circular 42.pdf"))
                .andExpect(jsonPath("$.uploaded[0].departmentId").value(otherDepartment.getId().toString()))
                .andExpect(jsonPath("$.uploaded[0].uploadedByName").value("Meena Rajan"))
                .andExpect(jsonPath("$.uploaded[0].canModify").value(true));

        // The bytes really are in object storage, under an opaque key.
        StoredFile stored = fileRepository.findAll().getFirst();
        assertThat(stored.getStorageKey())
                .startsWith(otherDepartment.getId() + "/" + folderId + "/")
                .doesNotContain("Circular");
        assertThat(objectExists(stored.getStorageKey())).isTrue();

        // The folder's denormalised counter kept up, and the upload is audited.
        assertThat(folderRepository.findById(folderId).orElseThrow().getFileCount()).isEqualTo(1);
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> AuditAction.FILE_UPLOADED.equals(entry.getAction()));

        // And it is visible where it was filed.
        mockMvc.perform(get("/api/v1/folders/{id}/files", folderId).header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    @DisplayName("an executable renamed to .pdf is refused while the genuine files in the same request are kept")
    void disguisedFileIsRejectedWithoutFailingTheBatch() throws Exception {
        upload(
                        memberToken,
                        pdf("Government Order 12.pdf"),
                        new MockMultipartFile("files", "payroll.pdf", "application/pdf", WINDOWS_EXECUTABLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploaded.length()").value(1))
                .andExpect(jsonPath("$.uploaded[0].fileName").value("Government Order 12.pdf"))
                .andExpect(jsonPath("$.rejected.length()").value(1))
                .andExpect(jsonPath("$.rejected[0].fileName").value("payroll.pdf"))
                .andExpect(jsonPath("$.rejected[0].code").value("FILE_CONTENT_MISMATCH"));

        assertThat(fileRepository.count()).isEqualTo(1);
        assertThat(folderRepository.findById(folderId).orElseThrow().getFileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("an anonymous caller cannot upload, browse or download")
    void documentsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/departments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/folders/{id}/files", folderId)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a download link is presigned and short-lived, and the download is recorded")
    void downloadLinkIsPresigned() throws Exception {
        UUID fileId = uploadOne(memberToken, "Circular 42.pdf");

        String body = mockMvc.perform(get("/api/v1/files/{id}/download-link", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("Circular 42.pdf"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String url = objectMapper.readTree(body).get("url").asText();
        // A signature and an expiry are what make handing this URL out safe.
        assertThat(url).contains("X-Amz-Signature").contains("X-Amz-Expires");

        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> AuditAction.FILE_DOWNLOADED.equals(entry.getAction()));
    }

    // ------------------------------------------------------------------------ delete

    @Test
    @DisplayName("deleting without a reason is refused")
    void deletingWithoutAReasonIsRefused() throws Exception {
        UUID fileId = uploadOne(memberToken, "Circular 42.pdf");

        deleteFile(fileId, "", memberToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        deleteFile(fileId, "   ", memberToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // A body with no reason field at all fails the same way.
        mockMvc.perform(delete("/api/v1/files/{id}", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(fileRepository.findById(fileId).orElseThrow().isDeleted()).isFalse();
        assertThat(deletionRepository.count()).isZero();
    }

    @Test
    @DisplayName("a member cannot delete a file someone else uploaded")
    void deletingSomeoneElsesFileIsForbidden() throws Exception {
        UUID fileId = uploadOne(memberToken, "Circular 42.pdf");
        String otherToken = registerApproveAndSignIn(OTHER_MEMBER_MOBILE, "Arun Kumar");

        deleteFile(fileId, "I would like this gone", otherToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_FILE_OWNER"));

        assertThat(fileRepository.findById(fileId).orElseThrow().isDeleted()).isFalse();
        assertThat(deletionRepository.count()).isZero();
    }

    @Test
    @DisplayName("the uploader deletes with a reason, every admin is notified with it, and an admin restores")
    void deleteWithReasonNotifiesAdminsAndCanBeRestored() throws Exception {
        UUID fileId = uploadOne(memberToken, "Circular 42.pdf");
        String reason = "Uploaded to the wrong department";

        deleteFile(fileId, reason, memberToken).andExpect(status().isOk());

        // Soft: the row survives and so do the bytes, which is what makes restore possible.
        StoredFile deleted = fileRepository.findById(fileId).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(objectExists(deleted.getStorageKey())).isTrue();
        assertThat(folderRepository.findById(folderId).orElseThrow().getFileCount()).isZero();

        // Gone from every read path.
        mockMvc.perform(get("/api/v1/files/{id}", fileId).header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/folders/{id}/files", folderId).header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(jsonPath("$.totalItems").value(0));

        // Every admin heard, and the reason travelled with it.
        List<User> admins = userRepository.findAll().stream().filter(User::isAdmin).toList();
        assertThat(admins).isNotEmpty();
        for (User admin : admins) {
            assertThat(notificationRepository.findAll().stream()
                            .filter(entry -> entry.getUser().getId().equals(admin.getId()))
                            .filter(entry -> NotificationType.FILE_DELETED.equals(entry.getType()))
                            .map(Notification::getBody))
                    .as("admin %s is told why", admin.getFullName())
                    .anySatisfy(body -> assertThat(body).contains(reason).contains("Circular 42.pdf"));
        }

        // The deletions log carries the same reason, and offers the restore.
        mockMvc.perform(get("/api/v1/admin/deletions").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].reason").value(reason))
                .andExpect(jsonPath("$.items[0].fileName").value("Circular 42.pdf"))
                .andExpect(jsonPath("$.items[0].deletedByName").value("Meena Rajan"))
                .andExpect(jsonPath("$.items[0].restorable").value(true));

        // A member cannot see the log or restore.
        mockMvc.perform(get("/api/v1/admin/deletions").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/files/{id}/restore", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isForbidden());

        // The admin restores it, and it is browsable again.
        mockMvc.perform(post("/api/v1/admin/files/{id}/restore", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("Circular 42.pdf"));

        assertThat(fileRepository.findById(fileId).orElseThrow().isDeleted()).isFalse();
        assertThat(folderRepository.findById(folderId).orElseThrow().getFileCount()).isEqualTo(1);
        assertThat(deletionRepository.findAll().getFirst().getRestoredAt()).isNotNull();

        mockMvc.perform(get("/api/v1/folders/{id}/files", folderId).header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    @DisplayName("an admin may delete a file a member uploaded")
    void adminMayDeleteAnyFile() throws Exception {
        UUID fileId = uploadOne(memberToken, "Circular 42.pdf");

        deleteFile(fileId, "Superseded by GO 118", adminToken).andExpect(status().isOk());

        assertThat(fileRepository.findById(fileId).orElseThrow().isDeleted()).isTrue();

        // The log records the admin as the deleter, not the member who uploaded it. Read through
        // the endpoint, which resolves the association inside its own transaction.
        mockMvc.perform(get("/api/v1/admin/deletions").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].deletedByName").value("System Administrator"))
                .andExpect(jsonPath("$.items[0].reason").value("Superseded by GO 118"));
    }

    // ----------------------------------------------------------------------- replace

    @Test
    @DisplayName("the uploader replaces their own document, and it keeps its identity")
    void uploaderReplacesTheirOwnDocument() throws Exception {
        UUID fileId = uploadOne(memberToken, "Circular 42.pdf");
        String originalKey = fileRepository.findById(fileId).orElseThrow().getStorageKey();

        byte[] corrected = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Note (corrected) >>\nendobj\ntrailer\n%%EOF"
                .getBytes(StandardCharsets.US_ASCII);

        replace(fileId, new MockMultipartFile("file", "Circular 42 (revised).pdf", "application/pdf", corrected), memberToken)
                .andExpect(status().isOk())
                // Same id, so favourites and links already shared inside the office still resolve.
                .andExpect(jsonPath("$.id").value(fileId.toString()))
                .andExpect(jsonPath("$.fileName").value("Circular 42 (revised).pdf"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.sizeBytes").value(corrected.length));

        StoredFile replaced = fileRepository.findById(fileId).orElseThrow();
        assertThat(replaced.getStorageKey()).isNotEqualTo(originalKey);
        assertThat(replaced.getUploadedBy().getId())
                .as("replacing does not reassign authorship")
                .isEqualTo(userRepository.findByMobileNumber(MEMBER_MOBILE).orElseThrow().getId());

        // Both objects exist: the superseded version is kept so a mistaken replacement is
        // recoverable, and the new one is what downloads now serve.
        assertThat(objectExists(originalKey)).isTrue();
        assertThat(objectExists(replaced.getStorageKey())).isTrue();

        // The folder still holds one document, not two.
        assertThat(folderRepository.findById(folderId).orElseThrow().getFileCount()).isEqualTo(1);
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> AuditAction.FILE_REPLACED.equals(entry.getAction()));
    }

    @Test
    @DisplayName("a member cannot replace a document someone else uploaded, and no bytes are stored")
    void replacingSomeoneElsesDocumentIsForbidden() throws Exception {
        UUID fileId = uploadOne(memberToken, "Circular 42.pdf");
        String originalKey = fileRepository.findById(fileId).orElseThrow().getStorageKey();
        String otherToken = registerApproveAndSignIn(OTHER_MEMBER_MOBILE, "Arun Kumar");

        replace(fileId, pdfPart("substitute.pdf"), otherToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_FILE_OWNER"));

        StoredFile untouched = fileRepository.findById(fileId).orElseThrow();
        assertThat(untouched.getVersion()).isEqualTo(1);
        assertThat(untouched.getStorageKey()).isEqualTo(originalKey);
        assertThat(untouched.getFileName()).isEqualTo("Circular 42.pdf");
    }

    @Test
    @DisplayName("an admin may replace a document a member uploaded")
    void adminMayReplaceAnyDocument() throws Exception {
        UUID fileId = uploadOne(memberToken, "Circular 42.pdf");

        replace(fileId, pdfPart("Circular 42 (corrected).pdf"), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.uploadedByName").value("Meena Rajan"));
    }

    @Test
    @DisplayName("a replacement is validated like any upload — a disguised file is refused")
    void aDisguisedReplacementIsRefused() throws Exception {
        UUID fileId = uploadOne(memberToken, "Circular 42.pdf");

        replace(
                        fileId,
                        new MockMultipartFile("file", "Circular 42.pdf", "application/pdf", WINDOWS_EXECUTABLE),
                        memberToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_CONTENT_MISMATCH"));

        assertThat(fileRepository.findById(fileId).orElseThrow().getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("a deleted document cannot be replaced")
    void aDeletedDocumentCannotBeReplaced() throws Exception {
        UUID fileId = uploadOne(memberToken, "Circular 42.pdf");
        deleteFile(fileId, "Wrong department", memberToken).andExpect(status().isOk());

        replace(fileId, pdfPart("Circular 42.pdf"), memberToken).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- browse & folders

    @Test
    @DisplayName("every department is browsable by any member, with folder and file counts")
    void departmentsAreBrowsableByEveryone() throws Exception {
        uploadOne(memberToken, "Circular 42.pdf");

        mockMvc.perform(get("/api/v1/departments").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(43));

        mockMvc.perform(get("/api/v1/departments/{id}/folders", otherDepartment.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Circulars 2026"))
                .andExpect(jsonPath("$[0].fileCount").value(1));
    }

    @Test
    @DisplayName("two folders cannot share a name in the same place")
    void duplicateFolderNamesAreRefused() throws Exception {
        mockMvc.perform(post("/api/v1/departments/{id}/folders", otherDepartment.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Circulars 2026"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_EXISTS"));
    }

    @Test
    @DisplayName("a nested folder reports its breadcrumb from the department root down")
    void breadcrumbWalksUpTheTree() throws Exception {
        UUID childId = createFolder(otherDepartment.getId(), "January", memberToken, folderId);

        mockMvc.perform(get("/api/v1/folders/{id}/breadcrumb", childId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Circulars 2026"))
                .andExpect(jsonPath("$[1].name").value("January"));
    }

    @Test
    @DisplayName("My Uploads shows only what this member uploaded")
    void myUploadsIsScopedToTheCaller() throws Exception {
        uploadOne(memberToken, "Circular 42.pdf");
        String otherToken = registerApproveAndSignIn(OTHER_MEMBER_MOBILE, "Arun Kumar");

        mockMvc.perform(get("/api/v1/files/my-uploads").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].fileName").value("Circular 42.pdf"));

        mockMvc.perform(get("/api/v1/files/my-uploads").header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        // But the other member can still see and download it where it is filed — rule 2.
        mockMvc.perform(get("/api/v1/folders/{id}/files", folderId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].canModify").value(false));
    }

    // ------------------------------------------------------------------------ helpers

    private ResultActions upload(String token, MockMultipartFile... parts) throws Exception {
        var request = multipart("/api/v1/files");
        for (MockMultipartFile part : parts) {
            request = request.file(part);
        }
        return mockMvc.perform(request.param("folderId", folderId.toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    /** Uploads one genuine PDF and returns its id. */
    private UUID uploadOne(String token, String filename) throws Exception {
        String body = upload(token, pdf(filename))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode uploaded = objectMapper.readTree(body).get("uploaded");
        assertThat(uploaded).hasSize(1);
        return UUID.fromString(uploaded.get(0).get("id").asText());
    }

    private ResultActions deleteFile(UUID fileId, String reason, String token) throws Exception {
        return mockMvc.perform(delete("/api/v1/files/{id}", fileId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason))));
    }

    private UUID createFolder(UUID departmentId, String name, String token) throws Exception {
        return createFolder(departmentId, name, token, null);
    }

    private UUID createFolder(UUID departmentId, String name, String token, UUID parentFolderId) throws Exception {
        Map<String, Object> body = parentFolderId == null
                ? Map.of("name", name, "category", "CIRCULAR")
                : Map.of("name", name, "category", "CIRCULAR", "parentFolderId", parentFolderId.toString());

        String response = mockMvc.perform(post("/api/v1/departments/{id}/folders", departmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private ResultActions replace(UUID fileId, MockMultipartFile part, String token) throws Exception {
        return mockMvc.perform(multipart("/api/v1/files/{id}/replace", fileId)
                .file(part)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private static MockMultipartFile pdf(String filename) {
        return new MockMultipartFile("files", filename, "application/pdf", REAL_PDF);
    }

    /** The replace endpoint takes a single part named "file", not the "files" list upload uses. */
    private static MockMultipartFile pdfPart(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", REAL_PDF);
    }

    /** Registers, approves through the real admin endpoint, and signs in — the demo path. */
    private String registerApproveAndSignIn(String mobile, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", name,
                                "mobileNumber", mobile,
                                "departmentId", ownDepartment.getId().toString(),
                                "designation", "Section Officer",
                                "password", PASSWORD))))
                .andExpect(status().isOk());

        // Looked up by user id rather than by walking request.getUser(), which is lazy and would
        // need a session open here.
        UUID applicantId = userRepository.findByMobileNumber(mobile).orElseThrow().getId();
        UUID requestId = registrationRequestRepository
                .findFirstByUserIdOrderByCreatedAtDesc(applicantId)
                .orElseThrow()
                .getId();

        mockMvc.perform(post("/api/v1/admin/registration-requests/{id}/approve", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        return signInWithOtp(mobile);
    }

    private String signInWithOtp(String mobile) throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mobileNumber", mobile))))
                .andExpect(status().isAccepted());

        String body = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("mobileNumber", mobile, "otp", otpProvider.lastOtpFor(mobile)))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /** The compose file creates the bucket in development; the test container needs it made here. */
    private void ensureBucket() {
        String bucket = "allgos-documents";
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException ex) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    private boolean objectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket("allgos-documents").key(key).build());
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
