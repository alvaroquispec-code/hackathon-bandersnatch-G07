package com.tuckersoft.branchengine.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    public ApiException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public static ApiException notFound(String m)     { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", m); }
    public static ApiException forbidden(String m)    { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", m); }
    public static ApiException conflict(String m)     { return new ApiException(HttpStatus.CONFLICT, "CONFLICT", m); }
    public static ApiException badRequest(String m)   { return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", m); }
    public static ApiException unauthorized(String m) { return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", m); }
}
