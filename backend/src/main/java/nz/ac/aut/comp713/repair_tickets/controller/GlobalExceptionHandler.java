package nz.ac.aut.comp713.repair_tickets.controller;

import jakarta.servlet.http.HttpServletRequest;
import nz.ac.aut.comp713.repair_tickets.dto.ApiError;
import nz.ac.aut.comp713.repair_tickets.exception.BusinessRuleException;
import nz.ac.aut.comp713.repair_tickets.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 把各種例外集中轉成「HTTP 狀態碼 + 統一格式的 JSON」。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 400：@Valid 檢查沒過，列出每個欄位的錯誤。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid", fields, req);
    }

    /** 400：JSON 格式錯誤、缺少 body、或 status 不是合法的值。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is missing, is not valid JSON, or contains an unknown value", Map.of(), req);
    }

    /** 400：網址參數型別錯誤，例如 ?status=FOO 或 /api/tickets/abc。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'", Map.of(), req);
    }

    /** 404：找不到工單。 */
    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), Map.of(), req);
    }

    /** 409：請求格式正確，但違反商業規則（跳步、不能取消）。 */
    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiError> conflict(BusinessRuleException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), Map.of(), req);
    }

    /** 其他情況：Spring 內建的錯誤（例如 404 網址不存在、405 方法不支援）沿用它的狀態碼，其餘一律 500。 */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> other(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse springError) {
            HttpStatusCode code = springError.getStatusCode();
            return build(code, "HTTP_" + code.value(), ex.getMessage(), Map.of(), req);
        }
        log.error("Unexpected error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", Map.of(), req);
    }

    private ResponseEntity<ApiError> build(HttpStatusCode status, String error, String message,
                                           Map<String, String> fields, HttpServletRequest req) {
        ApiError body = new ApiError(LocalDateTime.now(), status.value(), error, message, req.getRequestURI(), fields);
        return ResponseEntity.status(status).body(body);
    }
}
