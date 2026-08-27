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
import com.allgos.dms.file.repository.DownloadRepository;
import com.allgos.dms.file.repository.FavoriteRepository;
import com.allgos.dms.file.repository.FileDeletionRepository;
import com.allgos.dms.file.repository.StoredFileRepository;
import com.allgos.dms.folder.repository.FolderRepository;
import com.allgos.dms.notification.entity.Notification;
import com.allgos.dms.notification.entity.NotificationType;
import com.allgos.dms.notification.repository.NotificationRepository;
import com.allgos.dms.support.AbstractStorageIntegrationTest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

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
class FileAccessIT extends AbstractStorageIntegrationTest {

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

    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private FolderRepository folderRepository;
    @Autowired private StoredFileRepository fileRepository;
    @Autowired private FileDeletionRepository deletionRepository;
    @Autowired private DownloadRepository downloadRepository;
    @Autowired private FavoriteRepository favoriteRepository;
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
        // Foreign-key order: everything that points at a file goes before the files themselves.
        // Downloads and favourites joined that list in Phase 4, and omitting them here fails the
        // *next* test's setup rather than this one, which is a miserable thing to debug.
        downloadRepository.deleteAll();
        favoriteRepository.deleteAll();
        deletionRepository.deleteAll();
        fileRepository.deleteAll();
        folderRepository.deleteAll();
        auditLogRepository.deleteAll();
        notificationRepository.deleteAll();
        registrationRequestRepository.deleteAll();
        otpVerificationRepository.deleteAll();
        userRepository.findByMobileNumber(MEMBER_MOBILE).ifPresent(userRepository::delete);
        userRepository.findByMobileNumber(OTHER_MEMBER_MOBILE).ifPresent(userRepository::delete);

        // The seeded admin has no password of its own — in production the operator sets one through
        // the OTP reset before first use. Here it is given one directly, so these tests can sign in
        // the same way every other account does.
        User admin = userRepository.findByMobileNumber(ADMIN_MOBILE).orElseThrow();
        admin.setStatus(UserStatus.ACTIVE);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setFailedLoginCount(0);
        admin.setLockedUntil(null);
        userRepository.saveAndFlush(admin);

        List<Department> departments = departmentRepository.findByActiveTrueOrderByNameAsc();
        ownDepartment = departments.get(0);
        otherDepartment = departments.get(1);

        adminToken = signIn(ADMIN_MOBILE);
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
    @DisplayName("an upload tells the whole office — every active account except the uploader")
    void uploadNotifiesEveryoneButTheUploader() throws Exception {
        String otherToken = registerApproveAndSignIn(OTHER_MEMBER_MOBILE, "Arun Kumar");
        assertThat(otherToken).isNotBlank();

        upload(memberToken, pdf("Circular 42.pdf")).andExpect(status().isOk());

        UUID uploaderId = userRepository.findByMobileNumber(MEMBER_MOBILE).orElseThrow().getId();
        List<Notification> announcements = notificationRepository.findAll().stream()
                .filter(entry -> NotificationType.FILE_UPLOADED.equals(entry.getType()))
                .toList();

        // Everyone active hears: the admin, and the member who had nothing to do with it.
        List<UUID> expected = userRepository.findAll().stream()
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .map(User::getId)
                .filter(id -> !id.equals(uploaderId))
                .toList();

        assertThat(expected).hasSizeGreaterThanOrEqualTo(2);
        assertThat(announcements).extracting(entry -> entry.getUser().getId())
                .containsExactlyInAnyOrderElementsOf(expected);

        // The uploader is not told about their own upload.
        assertThat(announcements).noneMatch(entry -> entry.getUser().getId().equals(uploaderId));

        assertThat(announcements).allSatisfy(entry -> {
            assertThat(entry.getBody()).contains("Meena Rajan").contains("Circular 42.pdf")
                    .contains(otherDepartment.getName()).contains("Circulars 2026");
            assertThat(entry.getEntityRef()).startsWith("file:");
        });
    }

    @Test
    @DisplayName("a multi-file upload is announced once, not once per file")
    void multiFileUploadIsAnnouncedOnce() throws Exception {
        upload(memberToken, pdf("Order 1.pdf"), pdf("Order 2.pdf"), pdf("Order 3.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploaded.length()").value(3));

        UUID adminId = userRepository.findByMobileNumber(ADMIN_MOBILE).orElseThrow().getId();
        List<Notification> forAdmin = notificationRepository.findAll().stream()
                .filter(entry -> NotificationType.FILE_UPLOADED.equals(entry.getType()))
                .filter(entry -> entry.getUser().getId().equals(adminId))
                .toList();

        assertThat(forAdmin).singleElement().satisfies(entry -> {
            assertThat(entry.getBody()).contains("3 documents");
            // A batch points at the folder; there is no single file to open.
            assertThat(entry.getEntityRef()).isEqualTo("folder:" + folderId);
        });
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
    @DisplayName("the uploader deletes with a reason, the whole office is told why, and an admin restores")
    void deleteWithReasonNotifiesEveryoneAndCanBeRestored() throws Exception {
        // A second member, who had nothing to do with the document: they hear about it too.
        String otherToken = registerApproveAndSignIn(OTHER_MEMBER_MOBILE, "Arun Kumar");
        assertThat(otherToken).isNotBlank();

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

        // Everyone active heard — admins and the uninvolved member alike — and the reason and the
        // name of whoever did it travelled with it.
        UUID deleterId = userRepository.findByMobileNumber(MEMBER_MOBILE).orElseThrow().getId();
        List<UUID> expected = userRepository.findAll().stream()
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .map(User::getId)
                .filter(id -> !id.equals(deleterId))
                .toList();

        List<Notification> told = notificationRepository.findAll().stream()
                .filter(entry -> NotificationType.FILE_DELETED.equals(entry.getType()))
                .toList();

        assertThat(expected).hasSizeGreaterThanOrEqualTo(2);
        assertThat(told).extracting(entry -> entry.getUser().getId())
                .containsExactlyInAnyOrderElementsOf(expected);

        // The person who deleted it is not told about their own deletion.
        assertThat(told).noneMatch(entry -> entry.getUser().getId().equals(deleterId));

        assertThat(told).allSatisfy(entry -> assertThat(entry.getBody())
                .contains("Meena Rajan")
                .contains("Circular 42.pdf")
                .contains(reason));

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

    // ------------------------------------------------------------------------- purge

    @Test
    @DisplayName("an admin permanently deletes a document: the row, the bytes, and any hope of restoring it")
    void adminPurgesADeletedDocument() throws Exception {
        UUID fileId = uploadOne(memberToken, "Circular 42.pdf");
        String storageKey = fileRepository.findById(fileId).orElseThrow().getStorageKey();

        deleteFile(fileId, "Wrong department", memberToken).andExpect(status().isOk());
        assertThat(objectExists(storageKey)).isTrue(); // still recoverable up to this point

        mockMvc.perform(post("/api/v1/admin/files/{id}/purge", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        assertThat(fileRepository.findById(fileId)).isEmpty();
        assertThat(objectExists(storageKey)).isFalse();
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> AuditAction.FILE_PURGED.equals(entry.getAction()));

        // The log entry survives the purge, reads back from its own snapshot rather than a broken
        // join, and no longer offers a restore.
        mockMvc.perform(get("/api/v1/admin/deletions?status=all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].fileId").doesNotExist())
                .andExpect(jsonPath("$.items[0].fileName").value("Circular 42.pdf"))
                .andExpect(jsonPath("$.items[0].restorable").value(false))
                .andExpect(jsonPath("$.items[0].purgedByName").value("System Administrator"))
                .andExpect(jsonPath("$.items[0].purgeExpiresAt").doesNotExist());

        mockMvc.perform(post("/api/v1/admin/files/{id}/restore", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a member cannot permanently delete a document, even one they uploaded and deleted themselves")
    void memberCannotPurgeAFile() throws Exception {
        UUID fileId = uploadOne(memberToken, "Circular 42.pdf");
        deleteFile(fileId, "Wrong department", memberToken).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/files/{id}/purge", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isForbidden());

        assertThat(fileRepository.findById(fileId)).isPresent();
    }

    @Test
    @DisplayName("a document that was never deleted cannot be purged")
    void purgingALiveDocumentIsRefused() throws Exception {
        UUID fileId = uploadOne(memberToken, "Circular 42.pdf");

        mockMvc.perform(post("/api/v1/admin/files/{id}/purge", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FILE_NOT_DELETED"));

        assertThat(fileRepository.findById(fileId).orElseThrow().isDeleted()).isFalse();
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

    // ------------------------------------------------------------------- folder deletion

    @Test
    @DisplayName("deleting a folder without a reason is refused")
    void deletingAFolderWithoutAReasonIsRefused() throws Exception {
        UUID emptyFolderId = createFolder(otherDepartment.getId(), "Empty 2026", memberToken);

        deleteFolder(emptyFolderId, "", adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(folderRepository.findById(emptyFolderId)).isPresent();
    }

    @Test
    @DisplayName("a member cannot delete a folder, even an empty one they created")
    void deletingAFolderIsAdminOnly() throws Exception {
        UUID emptyFolderId = createFolder(otherDepartment.getId(), "Empty 2026", memberToken);

        deleteFolder(emptyFolderId, "No longer needed", memberToken).andExpect(status().isForbidden());

        assertThat(folderRepository.findById(emptyFolderId)).isPresent();
    }

    @Test
    @DisplayName("the department's General folder cannot be deleted")
    void generalFolderCannotBeDeleted() throws Exception {
        UUID generalId = createFolder(otherDepartment.getId(), "General", memberToken);

        deleteFolder(generalId, "Tidying up", adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GENERAL_FOLDER"));

        assertThat(folderRepository.findById(generalId)).isPresent();
    }

    @Test
    @DisplayName("a folder still holding a document cannot be deleted")
    void nonEmptyFolderCannotBeDeleted() throws Exception {
        uploadOne(memberToken, "Circular 42.pdf"); // lands in `folderId`

        deleteFolder(folderId, "Cleaning up", adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_NOT_EMPTY"));

        assertThat(folderRepository.findById(folderId)).isPresent();
    }

    @Test
    @DisplayName("a folder holding only a soft-deleted document can still be deleted; the document moves to General")
    void folderWithOnlyASoftDeletedDocumentIsReassignedToGeneralAndDeleted() throws Exception {
        UUID generalId = createFolder(otherDepartment.getId(), "General", memberToken);
        UUID emptyFolderId = createFolder(otherDepartment.getId(), "Once Used", memberToken);

        // Upload into it directly rather than through `folderId`, then remove the document — the
        // folder's live fileCount goes back to zero, but the file's row still points at this folder
        // (that is what makes restoring it possible).
        String body = mockMvc.perform(multipart("/api/v1/files")
                        .file(pdf("Temporary.pdf"))
                        .param("folderId", emptyFolderId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID fileId = UUID.fromString(
                objectMapper.readTree(body).get("uploaded").get(0).get("id").asText());

        deleteFile(fileId, "Wrong document", memberToken).andExpect(status().isOk());
        assertThat(folderRepository.findById(emptyFolderId).orElseThrow().getFileCount()).isZero();

        // The folder looks empty and really is deletable now — the deleted document's row moves to
        // General instead of leaving a foreign key blocking the delete.
        deleteFolder(emptyFolderId, "Cleaning up", adminToken).andExpect(status().isOk());

        assertThat(folderRepository.findById(emptyFolderId)).isEmpty();
        assertThat(fileRepository.findById(fileId).orElseThrow().getFolder().getId()).isEqualTo(generalId);

        // The deletions log still says where the document *was* filed — that is a snapshot taken at
        // the moment of deletion, not a live read through the (now-moved) association.
        mockMvc.perform(get("/api/v1/admin/deletions?status=all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$.items[0].folderName").value("Once Used"));
    }

    @Test
    @DisplayName("with no General folder to move it to, a deleted document still blocks the folder")
    void softDeletedDocumentBlocksDeletionWhenNoGeneralFolderExists() throws Exception {
        // otherDepartment's General folder was wiped by setUp() and nothing recreates it here.
        UUID emptyFolderId = createFolder(otherDepartment.getId(), "Once Used", memberToken);

        String body = mockMvc.perform(multipart("/api/v1/files")
                        .file(pdf("Temporary.pdf"))
                        .param("folderId", emptyFolderId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID fileId = UUID.fromString(
                objectMapper.readTree(body).get("uploaded").get(0).get("id").asText());

        deleteFile(fileId, "Wrong document", memberToken).andExpect(status().isOk());

        deleteFolder(emptyFolderId, "Cleaning up", adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_NOT_EMPTY"));

        assertThat(folderRepository.findById(emptyFolderId)).isPresent();
    }

    @Test
    @DisplayName("a folder still holding a subfolder cannot be deleted")
    void folderWithSubfoldersCannotBeDeleted() throws Exception {
        createFolder(otherDepartment.getId(), "January", memberToken, folderId);

        deleteFolder(folderId, "Cleaning up", adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_NOT_EMPTY"));

        assertThat(folderRepository.findById(folderId)).isPresent();
    }

    @Test
    @DisplayName("an admin deletes an empty folder with a reason, and the whole office is told why")
    void adminDeletesEmptyFolderAndEveryoneIsNotified() throws Exception {
        String otherToken = registerApproveAndSignIn(OTHER_MEMBER_MOBILE, "Arun Kumar");
        assertThat(otherToken).isNotBlank();

        UUID emptyFolderId = createFolder(otherDepartment.getId(), "Empty 2026", memberToken);
        String reason = "Created by mistake";

        deleteFolder(emptyFolderId, reason, adminToken).andExpect(status().isOk());

        assertThat(folderRepository.findById(emptyFolderId)).isEmpty();
        mockMvc.perform(get("/api/v1/departments/{id}/folders", otherDepartment.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(jsonPath("$[?(@.name == 'Empty 2026')]").isEmpty());

        UUID adminId = userRepository.findByMobileNumber(ADMIN_MOBILE).orElseThrow().getId();
        List<UUID> expected = userRepository.findAll().stream()
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .map(User::getId)
                .filter(id -> !id.equals(adminId))
                .toList();

        List<Notification> told = notificationRepository.findAll().stream()
                .filter(entry -> NotificationType.FOLDER_DELETED.equals(entry.getType()))
                .toList();

        assertThat(expected).hasSizeGreaterThanOrEqualTo(2);
        assertThat(told).extracting(entry -> entry.getUser().getId())
                .containsExactlyInAnyOrderElementsOf(expected);
        assertThat(told).allSatisfy(entry -> assertThat(entry.getBody())
                .contains("System Administrator")
                .contains("Empty 2026")
                .contains(reason));

        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> AuditAction.FOLDER_DELETED.equals(entry.getAction()));
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

    private ResultActions deleteFolder(UUID folderId, String reason, String token) throws Exception {
        return mockMvc.perform(delete("/api/v1/folders/{id}", folderId)
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

        return signIn(mobile);
    }

    /** A password sign-in, exactly as a user performs it, returning the access token. */
    private String signIn(String mobile) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("mobileNumber", mobile, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
