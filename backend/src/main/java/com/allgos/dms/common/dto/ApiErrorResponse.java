package com.allgos.dms.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** The single error shape every non-2xx response uses; mirrored by ApiError in the web app. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(String code, String message, Map<String, String> fieldErrors) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, null);
    }
}
