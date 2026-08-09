package com.allgos.dms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Every deliberate failure in the application is thrown as one of these, so that the HTTP status and
 * the machine-readable {@code code} the web app switches on are decided at the throw site rather
 * than guessed by the handler.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    // ------------------------------------------------------------------ common cases

    public static ApiException notFound(String entity) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", entity + " not found");
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException tooManyRequests(String code, String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }
}
