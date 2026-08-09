package com.allgos.dms.common.sms;

import com.allgos.dms.common.config.AppProperties;
import com.allgos.dms.common.exception.ApiException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * MSG91 driver, used in staging and production.
 *
 * <p>The message body itself lives in a DLT-approved template registered with the Indian regulator;
 * this call only supplies the template id and the variable, which is why no message text appears
 * here.
 */
@Component
@ConditionalOnProperty(name = "app.otp.provider", havingValue = "msg91")
public class Msg91OtpProvider implements OtpProvider {

    private static final Logger log = LoggerFactory.getLogger(Msg91OtpProvider.class);
    private static final String ENDPOINT = "https://control.msg91.com/api/v5/otp";

    private final AppProperties.Otp.Msg91 config;
    private final RestClient restClient;

    public Msg91OtpProvider(AppProperties properties, RestClient.Builder restClientBuilder) {
        this.config = properties.otp().msg91();
        this.restClient = restClientBuilder.build();

        if (config.authKey() == null || config.authKey().isBlank()) {
            throw new IllegalStateException(
                    "app.otp.provider is 'msg91' but MSG91_AUTH_KEY is not set");
        }
    }

    @Override
    public void sendOtp(String mobileNumber, String otp) {
        try {
            restClient
                    .post()
                    .uri(ENDPOINT)
                    .header("authkey", config.authKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "template_id", config.templateId(),
                            "sender", config.senderId(),
                            "mobile", "91" + mobileNumber,
                            "otp", otp))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            // The OTP is deliberately absent from this log line.
            log.error("MSG91 send failed for mobile ending {}", tail(mobileNumber), ex);
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "OTP_SEND_FAILED",
                    "Could not send the OTP right now. Please try again.");
        }
    }

    private static String tail(String mobileNumber) {
        return mobileNumber == null || mobileNumber.length() < 4
                ? "****"
                : mobileNumber.substring(mobileNumber.length() - 4);
    }
}
