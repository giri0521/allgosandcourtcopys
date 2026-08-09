package com.allgos.dms.common.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development driver: writes the OTP to the application log instead of sending an SMS.
 *
 * <p>This is the default provider so a developer can register and log in with no gateway account.
 * It logs the code in plaintext, which is precisely why {@code app.otp.provider} must be set to a
 * real gateway in staging and production.
 */
@Component
@ConditionalOnProperty(name = "app.otp.provider", havingValue = "mock", matchIfMissing = true)
public class MockOtpProvider implements OtpProvider {

    private static final Logger log = LoggerFactory.getLogger(MockOtpProvider.class);

    @Override
    public void sendOtp(String mobileNumber, String otp) {
        log.warn("=== MOCK OTP === mobile={} otp={} (development only; no SMS was sent)", mobileNumber, otp);
    }
}
