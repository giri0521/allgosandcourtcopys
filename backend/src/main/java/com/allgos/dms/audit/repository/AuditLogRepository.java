package com.allgos.dms.audit.repository;

import com.allgos.dms.audit.entity.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** The per-member activity screen. */
    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);

    /** The admin dashboard activity feed. */
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
