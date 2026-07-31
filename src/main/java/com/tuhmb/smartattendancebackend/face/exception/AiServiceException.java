package com.tuhmb.smartattendancebackend.face.exception;

import org.springframework.http.HttpStatus;

public class AiServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AiServiceException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public AiServiceException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
