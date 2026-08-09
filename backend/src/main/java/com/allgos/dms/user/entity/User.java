package com.allgos.dms.user.entity;

import com.allgos.dms.common.entity.BaseEntity;
import com.allgos.dms.department.entity.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    /** The login identity. 10 digits, no country code, unique across the system. */
    @Column(name = "mobile_number", nullable = false, unique = true)
    private String mobileNumber;

    @Column
    private String email;

    /** Null until the user sets one; the daily OTP is what actually admits them. */
    @Column(name = "password_hash")
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column
    private String designation;

    @Column(nullable = false)
    private UserRole role = UserRole.MEMBER;

    @Column(nullable = false)
    private UserStatus status = UserStatus.PENDING;

    /**
     * The last date, in the configured application timezone, on which this user completed an OTP
     * login. Password login is refused until this equals today — see LoginPolicyService.
     */
    @Column(name = "last_otp_login_date")
    private LocalDate lastOtpLoginDate;

    /** Bumped by "logout from all devices"; refresh tokens carrying an older value are rejected. */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }
}
