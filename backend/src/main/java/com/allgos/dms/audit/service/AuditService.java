package com.allgos.dms.audit.service;

import com.allgos.dms.audit.entity.AuditLog;
import com.allgos.dms.audit.repository.AuditLogRepository;
import com.allgos.dms.common.web.ClientIp;
import com.allgos.dms.user.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail.
 *
 * <p>There are two ways to record, and choosing the wrong one breaks things:
 *
 * <ul>
 *   <li>{@link #record} joins the caller's transaction. Use it for successful actions, including
 *       ones that reference a row created moments earlier in that same transaction — a registration
 *       audit cannot be written independently, because the user it points at is not committed yet
 *       and the foreign key would fail.
 *   <li>{@link #recordDurable} runs in its own transaction, so the entry survives the caller
 *       rolling back. Use it for rejected attempts, which are exactly the events that must not
 *       vanish with the exception that caused them. The actor must already be committed.
 * </ul>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(User actor, String action, String entityType, UUID entityId, Map<String, Object> metadata) {
        write(actor, action, entityType, entityId, metadata);
    }

    public void record(User actor, String action) {
        record(actor, action, null, null, null);
    }

    /**
     * Records an entry that outlives a rollback. The actor must already exist in the database, since
     * this transaction cannot see uncommitted rows from the caller's.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDurable(User actor, String action, String entityType, UUID entityId, Map<String, Object> metadata) {
        write(actor, action, entityType, entityId, metadata);
    }

    /** For pre-authentication events, where there is no actor but the mobile number matters. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAnonymous(String action, Map<String, Object> metadata) {
        write(null, action, null, null, metadata);
    }

    private void write(User actor, String action, String entityType, UUID entityId, Map<String, Object> metadata) {
        AuditLog entry = new AuditLog();
        entry.setActor(actor);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setIpAddress(ClientIp.current());
        entry.setMetadata(serialise(metadata));

        auditLogRepository.save(entry);
    }

    private String serialise(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            // Never let audit metadata break the action being audited.
            log.warn("Could not serialise audit metadata for action", ex);
            return null;
        }
    }

}
