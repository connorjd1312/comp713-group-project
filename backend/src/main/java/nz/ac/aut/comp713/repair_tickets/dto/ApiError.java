package nz.ac.aut.comp713.repair_tickets.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** 所有錯誤回應都用同一個 JSON 格式，前端只需要一種處理方式。 */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {
}
