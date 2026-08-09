package com.allgos.dms.auth.entity;

import com.allgos.dms.common.entity.BaseEntity;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The admin review queue. One row per self-registration, including admin self-registrations, which
 * an existing admin must approve so that nobody can grant themselves administrative access.
 */
@Entity
@Table(name = "registration_requests")
@Getter
@Setter
@NoArgsConstructor
public class RegistrationRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "requested_role", nullable = false)
    private UserRole requestedRole = UserRole.MEMBER;

    @Column(nullable = false)
    private RegistrationStatus status = RegistrationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    /** Required when rejecting, so the applicant can be told why. */
    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
