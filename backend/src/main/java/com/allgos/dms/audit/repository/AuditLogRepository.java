package com.allgos.dms.audit.repository;

import com.allgos.dms.audit.entity.AuditLog;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * The audit trail is read-only to the application: rows are written by {@link
 * com.allgos.dms.audit.service.AuditService} and never updated or deleted. There is deliberately no
 * delete method here — a trail somebody can edit is not evidence.
 *
 * <p>Filtering for the viewer goes through {@link JpaSpecificationExecutor} rather than a query with
 * optional parameters. Four independent filters would otherwise mean sixteen combinations to write
 * or a string of {@code :param is null or …} clauses, which Hibernate cannot always type when the
 * parameter is a UUID.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    /** The per-member activity screen. */
    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);

    /** The admin dashboard activity feed. */
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByActorIdAndAction(UUID actorId, String action);

    long countByActionAndCreatedAtAfter(String action, Instant after);
}
