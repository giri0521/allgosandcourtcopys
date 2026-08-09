package com.allgos.dms.support;

import com.allgos.dms.common.sms.OtpProvider;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Stands in for the SMS gateway during tests and remembers the last code sent to each number, so a
 * test can complete a real OTP sign-in exactly as a user would — the code is still generated,
 * hashed and verified by the production code path.
 */
public class RecordingOtpProvider implements OtpProvider {

    private final Map<String, String> lastOtpByMobile = new HashMap<>();

    @Override
    public void sendOtp(String mobileNumber, String otp) {
        lastOtpByMobile.put(mobileNumber, otp);
    }

    public String lastOtpFor(String mobileNumber) {
        String otp = lastOtpByMobile.get(mobileNumber);
        if (otp == null) {
            throw new IllegalStateException("No OTP was sent to " + mobileNumber);
        }
        return otp;
    }

    public void clear() {
        lastOtpByMobile.clear();
    }

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public RecordingOtpProvider recordingOtpProvider() {
            return new RecordingOtpProvider();
        }
    }
}
