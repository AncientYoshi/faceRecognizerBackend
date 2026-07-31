package com.tuhmb.smartattendancebackend.common.api;

import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.face.exception.AiServiceException;
import com.tuhmb.smartattendancebackend.face.exception.ImageValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "validation_error",
                "Request validation failed",
                request.getRequestURI(),
                fields
        );
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "validation_error", exception.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "invalid_request_body", "Request body is invalid", request);
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiError> handleInvalidRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "invalid_request", exception.getMessage(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleUnsupportedMedia(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "unsupported_media_type",
                "Request media type is not supported",
                request
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleUploadTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.CONTENT_TOO_LARGE,
                "image_too_large",
                "Uploaded image must not exceed 10 MB",
                request
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "authentication_failed", exception.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return error(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "You do not have permission for this action",
                request
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "resource_not_found", exception.getMessage(), request);
    }

    @ExceptionHandler(AiServiceException.class)
    ResponseEntity<ApiError> handleAiService(AiServiceException exception, HttpServletRequest request) {
        return error(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(ImageValidationException.class)
    ResponseEntity<ApiError> handleImageValidation(ImageValidationException exception, HttpServletRequest request) {
        return error(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
    ResponseEntity<ApiError> handleConflict(Exception exception, HttpServletRequest request) {
        String message = exception instanceof ConflictException
                ? exception.getMessage()
                : "The request conflicts with existing data";
        return error(HttpStatus.CONFLICT, "data_conflict", message, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "invalid_request", exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure for {}", request.getRequestURI(), exception);
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal_error",
                "An unexpected error occurred",
                request
        );
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(
                ApiError.of(status.value(), status.getReasonPhrase(), code, message, request.getRequestURI())
        );
    }
}
