package com.allgos.dms.audit.service;

import com.allgos.dms.audit.entity.AuditLog;
import com.allgos.dms.audit.repository.AuditLogRepository;
import com.allgos.dms.user.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes the audit trail.
 *
 * <p>Every call runs in its own transaction ({@code REQUIRES_NEW}) so that the record of an attempt
 * survives even when the surrounding business transaction rolls back — a rejected download or a
 * failed login is exactly the kind of event that must not vanish with the rollback.
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User actor, String action, String entityType, UUID entityId, Map<String, Object> metadata) {
        AuditLog entry = new AuditLog();
        entry.setActor(actor);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setIpAddress(currentIpAddress());
        entry.setMetadata(serialise(metadata));

        auditLogRepository.save(entry);
    }

    public void record(User actor, String action) {
        record(actor, action, null, null, null);
    }

    /** For pre-authentication events, where there is no actor but the mobile number matters. */
    public void recordAnonymous(String action, Map<String, Object> metadata) {
        record(null, action, null, null, metadata);
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

    private String currentIpAddress() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();

        // Behind the government/NIC reverse proxy the real client address arrives in this header.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
