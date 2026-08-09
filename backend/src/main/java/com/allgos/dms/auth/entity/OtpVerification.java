package com.allgos.dms.auth.entity;

import com.allgos.dms.common.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single issued OTP. The code itself is never stored — only a BCrypt hash — so a database read
 * cannot be replayed as a login.
 */
@Entity
@Table(name = "otp_verifications")
@Getter
@Setter
@NoArgsConstructor
public class OtpVerification extends BaseCreatedEntity {

    @Column(name = "mobile_number", nullable = false)
    private String mobileNumber;

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(nullable = false)
    private OtpPurpose purpose;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set once, on successful verification; a verified OTP can never be reused. */
    @Column(name = "verified_at")
    private Instant verifiedAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return verifiedAt != null;
    }
}
