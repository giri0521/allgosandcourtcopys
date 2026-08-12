package com.allgos.dms.admin.service;

import com.allgos.dms.admin.dto.AdminReports.ActivityReport;
import com.allgos.dms.admin.dto.AdminReports.ActivitySummary;
import com.allgos.dms.admin.dto.AdminReports.AuditEntry;
import com.allgos.dms.admin.dto.AdminReports.DepartmentActivity;
import com.allgos.dms.admin.dto.AdminReports.MemberActivity;
import com.allgos.dms.admin.dto.AdminReports.MonthlyActivity;
import com.allgos.dms.admin.dto.AdminReports.SystemStats;
import com.allgos.dms.admin.dto.AdminReports.UploaderActivity;
import com.allgos.dms.admin.dto.AdminResponses.MemberView;
import com.allgos.dms.audit.entity.AuditAction;
import com.allgos.dms.audit.entity.AuditLog;
import com.allgos.dms.audit.repository.AuditLogRepository;
import com.allgos.dms.auth.entity.RegistrationStatus;
import com.allgos.dms.auth.repository.RegistrationRequestRepository;
import com.allgos.dms.common.config.AppProperties;
import com.allgos.dms.common.dto.PageResponse;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.department.entity.Department;
import com.allgos.dms.department.repository.DepartmentRepository;
import com.allgos.dms.file.repository.DownloadRepository;
import com.allgos.dms.file.repository.FileDeletionRepository;
import com.allgos.dms.file.repository.StoredFileRepository;
import com.allgos.dms.file.repository.StoredFileRepository.DepartmentCount;
import com.allgos.dms.file.repository.StoredFileRepository.MonthCount;
import com.allgos.dms.folder.repository.FolderRepository;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserStatus;
import com.allgos.dms.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the office has been doing: the audit viewer, a member's activity, and the reports.
 *
 * <p>Everything here reads. Nothing in this class writes a row, and it deliberately offers no way to
 * remove one — an audit trail somebody can edit is not evidence, so there is no delete to call even
 * by mistake.
 *
 * <p>Periods are resolved in the application's timezone rather than UTC. "March" must mean the
 * office's March: a document filed at 4am IST on the 1st belongs to March, and a report that put it
 * in February would be quietly wrong in a way nobody would catch by reading the code.
 */
@Service
public class AdminReportService {

    /** Enough names to be useful without turning the report into a directory. */
    private static final int TOP_UPLOADERS = 10;

    /** A year reads as a trend; more is a wall of columns nobody scans. */
    private static final int MONTHS_IN_SERIES = 12;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final FolderRepository folderRepository;
    private final StoredFileRepository fileRepository;
    private final DownloadRepository downloadRepository;
    private final FileDeletionRepository deletionRepository;
    private final RegistrationRequestRepository registrationRequestRepository;
    private final ZoneId zone;

    public AdminReportService(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            FolderRepository folderRepository,
            StoredFileRepository fileRepository,
            DownloadRepository downloadRepository,
            FileDeletionRepository deletionRepository,
            RegistrationRequestRepository registrationRequestRepository,
            AppProperties properties) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.downloadRepository = downloadRepository;
        this.deletionRepository = deletionRepository;
        this.registrationRequestRepository = registrationRequestRepository;
        this.zone = ZoneId.of(properties.timezone());
    }

    // ------------------------------------------------------------------------- stats

    /** The dashboard tiles. */
    @Transactional(readOnly = true)
    public SystemStats stats() {
        Instant monthStart = startOfThisMonth();
        Instant now = Instant.now();

        return new SystemStats(
                departmentRepository.count(),
                folderRepository.count(),
                fileRepository.countByDeletedFalse(),
                userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                registrationRequestRepository.countByStatus(RegistrationStatus.PENDING),
                fileRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(monthStart, now),
                downloadRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(monthStart, now),
                // Removed and not put back — a restored document is not a deleted one.
                deletionRepository.countByRestoredAtIsNull());
    }

    // -------------------------------------------------------------- member activity

    /**
     * One member's history: what they have done, and the trail of it.
     *
     * <p>The timeline is the audit log filtered to this actor, which is the same evidence a security
     * review reads — not a separate, friendlier record that could disagree with it.
     */
    @Transactional(readOnly = true)
    public MemberActivity memberActivity(UUID userId, Pageable pageable) {
        User member = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("Member"));

        ActivitySummary summary = new ActivitySummary(
                fileRepository.countByUploadedByIdAndDeletedFalse(userId),
                downloadRepository.countByUserId(userId),
                deletionRepository.countByDeletedById(userId),
                auditLogRepository.countByActorIdAndAction(userId, AuditAction.OTP_VERIFIED)
                        + auditLogRepository.countByActorIdAndAction(userId, AuditAction.LOGIN_PASSWORD),
                member.getLastLoginAt());

        PageResponse<AuditEntry> timeline = PageResponse.of(
                auditLogRepository.findByActorIdOrderByCreatedAtDesc(userId, pageable), AuditEntry::from);

        return new MemberActivity(MemberView.from(member), summary, timeline);
    }

    // ----------------------------------------------------------------- audit viewer

    /**
     * The audit trail, filtered.
     *
     * <p>Built as a {@link Specification} rather than a query with four optional parameters: sixteen
     * combinations would otherwise be written by hand or expressed as {@code :param is null or …},
     * which Hibernate cannot reliably type for a UUID. Each filter here is simply omitted when it is
     * not given.
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> auditLogs(
            UUID actorId, String action, Instant from, Instant to, Pageable pageable) {

        Specification<AuditLog> spec = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actorId != null) {
                predicates.add(builder.equal(root.get("actor").get("id"), actorId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(builder.equal(root.get("action"), action.trim()));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThan(root.get("createdAt"), to));
            }
            return predicates.isEmpty() ? null : builder.and(predicates.toArray(Predicate[]::new));
        };

        return PageResponse.of(auditLogRepository.findAll(spec, pageable), AuditEntry::from);
    }

    /** The dashboard's activity feed: the whole trail, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> recentActivity(Pageable pageable) {
        return PageResponse.of(auditLogRepository.findAllByOrderByCreatedAtDesc(pageable), AuditEntry::from);
    }

    // ----------------------------------------------------------------------- reports

    /**
     * Uploads, downloads and holdings for a period.
     *
     * <p>{@code to} is exclusive throughout, which is why the callers pass the start of the day
     * after the one the user chose. Half-open ranges are the only way to avoid either losing the
     * last day's activity or counting a boundary document in two adjacent reports.
     *
     * @param from inclusive; defaults to the start of the month a year ago
     * @param to exclusive; defaults to now
     */
    @Transactional(readOnly = true)
    public ActivityReport report(Instant from, Instant to) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? defaultReportStart() : from;

        if (!start.isBefore(end)) {
            throw ApiException.badRequest("RANGE_INVALID", "The start of the period must come before its end.");
        }

        Map<UUID, Long> live = toMap(fileRepository.countLiveByDepartment());
        Map<UUID, Long> uploads = toMap(fileRepository.countUploadsByDepartment(start, end));
        Map<UUID, Long> downloads = toMap(downloadRepository.countByDepartment(start, end));

        // Every department appears, including the quiet ones: a report that silently omitted the
        // departments with no activity would make it impossible to notice that they have none.
        List<DepartmentActivity> departments = departmentRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(department -> new DepartmentActivity(
                        department.getId(),
                        department.getName(),
                        live.getOrDefault(department.getId(), 0L),
                        uploads.getOrDefault(department.getId(), 0L),
                        downloads.getOrDefault(department.getId(), 0L)))
                .sorted(Comparator.comparingLong(DepartmentActivity::uploads).reversed()
                        .thenComparing(DepartmentActivity::departmentName))
                .toList();

        List<UploaderActivity> topUploaders = fileRepository
                .countUploadsByUploader(start, end, PageRequest.of(0, TOP_UPLOADERS))
                .stream()
                .map(row -> new UploaderActivity(
                        row.getUserId(), row.getFullName(), row.getDepartmentName(), row.getTotal()))
                .toList();

        return new ActivityReport(start, end, departments, topUploaders, monthlySeries(start, end));
    }

    /**
     * The monthly series, with empty months filled in.
     *
     * <p>The database returns only months that have rows. Drawing that directly would compress a
     * quiet summer out of existence and make the chart lie about the shape of the year, so every
     * month in the range is present, zero or not.
     */
    private List<MonthlyActivity> monthlySeries(Instant start, Instant end) {
        Map<String, Long> uploads = toMonthMap(fileRepository.countUploadsByMonth(start, end));
        Map<String, Long> downloads = toMonthMap(downloadRepository.countByMonth(start, end));

        Map<String, MonthlyActivity> series = new LinkedHashMap<>();
        YearMonth cursor = YearMonth.from(LocalDate.ofInstant(start, zone));
        YearMonth last = YearMonth.from(LocalDate.ofInstant(end, zone));

        while (!cursor.isAfter(last)) {
            String key = cursor.toString(); // YYYY-MM, which sorts as it reads
            series.put(
                    key,
                    new MonthlyActivity(key, uploads.getOrDefault(key, 0L), downloads.getOrDefault(key, 0L)));
            cursor = cursor.plusMonths(1);
        }

        return List.copyOf(series.values());
    }

    // ----------------------------------------------------------------------- helpers

    private Instant startOfThisMonth() {
        return YearMonth.now(zone).atDay(1).atStartOfDay(zone).toInstant();
    }

    private Instant defaultReportStart() {
        return YearMonth.now(zone)
                .minusMonths(MONTHS_IN_SERIES - 1L)
                .atDay(1)
                .atStartOfDay(zone)
                .toInstant();
    }

    private static Map<UUID, Long> toMap(List<DepartmentCount> rows) {
        return rows.stream()
                .collect(Collectors.toMap(
                        DepartmentCount::getDepartmentId, DepartmentCount::getTotal, Long::sum, HashMap::new));
    }

    private static Map<String, Long> toMonthMap(List<MonthCount> rows) {
        return rows.stream()
                .collect(Collectors.toMap(MonthCount::getMonth, MonthCount::getTotal, Long::sum, HashMap::new));
    }
}
