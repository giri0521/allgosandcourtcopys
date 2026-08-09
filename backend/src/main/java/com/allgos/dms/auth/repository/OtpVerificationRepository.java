package com.allgos.dms.auth.repository;

import com.allgos.dms.auth.entity.OtpPurpose;
import com.allgos.dms.auth.entity.OtpVerification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {

    /** The OTP a verify attempt is checked against: the most recent one for this mobile + purpose. */
    Optional<OtpVerification> findFirstByMobileNumberAndPurposeOrderByCreatedAtDesc(
            String mobileNumber, OtpPurpose purpose);

    /**
     * The most recent code sent to this number for any purpose. The resend cooldown is enforced
     * across purposes so that alternating between them cannot be used to send faster.
     */
    Optional<OtpVerification> findFirstByMobileNumberOrderByCreatedAtDesc(String mobileNumber);

    /** Backs the per-mobile hourly send limit. */
    long countByMobileNumberAndCreatedAtAfter(String mobileNumber, Instant since);
}
