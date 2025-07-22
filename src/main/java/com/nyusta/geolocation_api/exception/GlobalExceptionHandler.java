package com.nyusta.geolocation_api.exception;

import com.nyusta.geolocation_api.payload.response.GeoLocationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GeoLocationResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Invalid argument: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(GeoLocationResponse.error("Invalid input: " + e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<GeoLocationResponse> handleIllegalStateException(IllegalStateException e) {
        log.error("Service unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(GeoLocationResponse.error("Service temporarily unavailable"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GeoLocationResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() != null ?
                e.getBindingResult().getFieldError().getDefaultMessage() : "Validation failed";
        log.warn("Validation error: {}", message);
        return ResponseEntity.badRequest()
                .body(GeoLocationResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GeoLocationResponse> handleGenericException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.internalServerError()
                .body(GeoLocationResponse.error("An unexpected error occurred"));
    }
}
