package com.allgos.dms.admin.dto;

import com.allgos.dms.admin.dto.AdminResponses.MemberView;
import com.allgos.dms.audit.entity.AuditLog;
import com.allgos.dms.common.dto.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response bodies for the admin monitoring and reporting screens. */
public final class AdminReports {

    /**
     * One line of the audit trail, as a screen shows it.
     *
     * <p>{@code metadata} is passed through as the raw JSON string it is stored as. The web app
     * renders whatever keys it finds rather than the server inventing a sentence per action — new
     * actions then appear in the viewer without a backend change, and nothing is silently dropped
     * because no one wrote a template for it.
     */
    public record AuditEntry(
            UUID id,
            String action,
            UUID actorId,
            String actorName,
            String entityType,
            UUID entityId,
            String metadata,
            String ipAddress,
            Instant at) {

        /** Must be called inside the transaction — the actor is a lazy association. */
        public static AuditEntry from(AuditLog log) {
            return new AuditEntry(
                    log.getId(),
                    log.getAction(),
                    log.getActor() == null ? null : log.getActor().getId(),
                    // Null for events that happened before anyone was authenticated: a failed login
                    // against an unknown number has no actor to name.
                    log.getActor() == null ? null : log.getActor().getFullName(),
                    log.getEntityType(),
                    log.getEntityId(),
                    log.getMetadata(),
                    log.getIpAddress(),
                    log.getCreatedAt());
        }
    }

    /** The counters above a member's timeline. */
    public record ActivitySummary(
            long uploads, long downloads, long deletions, long logins, Instant lastLoginAt) {}

    /** Everything the per-member activity screen needs, in one call. */
    public record MemberActivity(
            MemberView member, ActivitySummary summary, PageResponse<AuditEntry> timeline) {}

    /** The tiles on the admin dashboard. */
    public record SystemStats(
            long departments,
            long folders,
            long documents,
            long members,
            long activeMembers,
            long pendingRequests,
            long uploadsThisMonth,
            long downloadsThisMonth,
            long deletedDocuments) {}

    /**
     * One department's line in the report.
     *
     * <p>{@code documents} is what it holds now; {@code uploads} and {@code downloads} are what
     * happened during the reporting period. A department can hold nothing and still have been busy,
     * which is why both are here.
     */
    public record DepartmentActivity(
            UUID departmentId, String departmentName, long documents, long uploads, long downloads) {}

    public record UploaderActivity(
            UUID userId, String fullName, String departmentName, long uploads) {}

    /** One month of the series. {@code month} is {@code YYYY-MM}, so it sorts as it reads. */
    public record MonthlyActivity(String month, long uploads, long downloads) {}

    /**
     * A whole report for a period.
     *
     * <p>Assembled in one call rather than three, because the three are always shown together and a
     * screen that fetched them separately could draw a chart from one period beside a table from
     * another while the requests settled.
     */
    public record ActivityReport(
            Instant from,
            Instant to,
            List<DepartmentActivity> departments,
            List<UploaderActivity> topUploaders,
            List<MonthlyActivity> monthly) {}

    private AdminReports() {}
}
