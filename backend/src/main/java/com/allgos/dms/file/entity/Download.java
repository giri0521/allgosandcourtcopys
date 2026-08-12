package com.allgos.dms.file.entity;

import com.allgos.dms.common.entity.BaseCreatedEntity;
import com.allgos.dms.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One document handed to one user.
 *
 * <p>Written when a presigned download URL is issued, which is the last moment the server is
 * involved — the bytes then travel from object storage directly to the browser. A row therefore
 * means "this user was given the means to read this document", which is the question an audit asks,
 * and not "these bytes definitely arrived".
 *
 * <p>Deliberately separate from {@code audit_logs}, which records the same event: the audit trail is
 * append-only evidence for a reviewer, while this is a user-facing history they can browse. Reports
 * count rows here; nobody paginates the audit log to build a screen.
 */
@Entity
@Table(name = "downloads")
@Getter
@Setter
@NoArgsConstructor
public class Download extends BaseCreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private StoredFile file;

    @Column(name = "ip_address")
    private String ipAddress;
}
