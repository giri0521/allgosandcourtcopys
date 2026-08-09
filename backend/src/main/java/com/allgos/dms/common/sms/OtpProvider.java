package com.allgos.dms.common.sms;

/**
 * How an OTP reaches the user's phone.
 *
 * <p>Kept behind an interface so development and testing never depend on a live SMS account, and so
 * the production gateway can be chosen — or changed — without touching the authentication logic.
 * India requires DLT registration of SMS templates, which has a lead time; the mock driver keeps the
 * team unblocked until that is approved.
 */
public interface OtpProvider {

    /**
     * @param mobileNumber 10-digit national number, no country code
     * @param otp the plaintext code; implementations must never log or persist it
     */
    void sendOtp(String mobileNumber, String otp);
}
