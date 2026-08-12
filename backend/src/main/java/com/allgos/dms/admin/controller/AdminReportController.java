package com.allgos.dms.admin.controller;

import com.allgos.dms.admin.dto.AdminReports.ActivityReport;
import com.allgos.dms.admin.dto.AdminReports.AuditEntry;
import com.allgos.dms.admin.dto.AdminReports.MemberActivity;
import com.allgos.dms.admin.dto.AdminReports.SystemStats;
import com.allgos.dms.admin.service.AdminReportService;
import com.allgos.dms.admin.service.CsvWriter;
import com.allgos.dms.audit.entity.AuditAction;
import com.allgos.dms.common.dto.PageResponse;
import com.allgos.dms.common.exception.ApiException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Admin monitoring: the dashboard, a member's activity, the audit viewer and the reports.
 *
 * <p>As in {@link AdminUserController}, the {@code @PreAuthorize} sits on the class so a method
 * added later cannot be left unguarded. Everything here is read-only — there is no endpoint that
 * edits or removes an audit row, by design.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AdminReportService reportService;
    private final ZoneId zone;

    public AdminReportController(
            AdminReportService reportService, com.allgos.dms.common.config.AppProperties properties) {
        this.reportService = reportService;
        this.zone = ZoneId.of(properties.timezone());
    }

    @GetMapping("/stats")
    public SystemStats stats() {
        return reportService.stats();
    }

    /** The dashboard's activity feed: everything, newest first. */
    @GetMapping("/activity")
    public PageResponse<AuditEntry> recentActivity(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return reportService.recentActivity(pageable(page, size));
    }

    @GetMapping("/members/{memberId}/activity")
    public MemberActivity memberActivity(
            @PathVariable UUID memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return reportService.memberActivity(memberId, pageable(page, size));
    }

    // ------------------------------------------------------------------ audit viewer

    /**
     * @param from inclusive, as a date in the application's timezone
     * @param to inclusive as the user means it — widened to the start of the next day, so the last
     *     day's entries are not silently dropped
     */
    @GetMapping("/audit-logs")
    public PageResponse<AuditEntry> auditLogs(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return reportService.auditLogs(actorId, action, startOf(from), endOf(to), pageable(page, size));
    }

    /** The values the viewer's filter offers, so the list cannot drift from what is recorded. */
    @GetMapping("/audit-logs/actions")
    public List<String> auditActions() {
        return AuditAction.all();
    }

    // ---------------------------------------------------------------------- reports

    @GetMapping("/reports")
    public ActivityReport report(
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return reportService.report(startOf(from), endOf(to));
    }

    /**
     * The same report as a spreadsheet.
     *
     * <p>Streamed rather than assembled into a String: the response is written straight to the
     * socket, so a long report never sits in memory twice. See {@link CsvWriter} for why the file
     * starts with a byte-order mark.
     */
    @GetMapping("/reports/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(defaultValue = "departments") String type,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {

        ActivityReport report = reportService.report(startOf(from), endOf(to));
        String filename = "%s-%s.csv".formatted(type, LocalDate.now(zone));

        List<String> headers;
        List<List<String>> rows = new ArrayList<>();

        switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "departments" -> {
                headers = List.of("Department", "Documents held", "Uploads", "Downloads");
                report.departments().forEach(row -> rows.add(List.of(
                        row.departmentName(),
                        String.valueOf(row.documents()),
                        String.valueOf(row.uploads()),
                        String.valueOf(row.downloads()))));
            }
            case "uploaders" -> {
                headers = List.of("Member", "Department", "Uploads");
                report.topUploaders().forEach(row -> rows.add(List.of(
                        row.fullName(),
                        row.departmentName() == null ? "" : row.departmentName(),
                        String.valueOf(row.uploads()))));
            }
            case "monthly" -> {
                headers = List.of("Month", "Uploads", "Downloads");
                report.monthly().forEach(row -> rows.add(
                        List.of(row.month(), String.valueOf(row.uploads()), String.valueOf(row.downloads()))));
            }
            default -> throw ApiException.badRequest(
                    "REPORT_UNKNOWN", "Unknown report: " + type);
        }

        StreamingResponseBody body = out -> {
            try {
                CsvWriter.write(out, headers, rows);
            } catch (IOException ex) {
                // The client hung up mid-download; nothing useful can be sent to them now.
                throw new IllegalStateException("Could not write the report", ex);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(filename))
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
    }

    // ----------------------------------------------------------------------- helpers

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    }

    /** A date the user chose, as the instant that day begins in the office's timezone. */
    private Instant startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay(zone).toInstant();
    }

    /**
     * The end of a period the user means inclusively.
     *
     * <p>"To 3 March" must include a document filed at 4pm on the 3rd, so the bound passed on is the
     * start of the 4th and every comparison against it is strictly less-than.
     */
    private Instant endOf(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay(zone).toInstant();
    }
}
