package nz.ac.aut.comp713.repair_tickets.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 客戶送修時前端傳來的 JSON。標註的規則會在進入 Controller 前自動檢查。 */
public record CreateTicketRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @Pattern(regexp = "^[0-9 +()-]{0,30}$", message = "Phone may only contain digits, spaces and + ( ) -")
        String phone,

        @NotBlank(message = "Device type is required")
        @Pattern(regexp = "(?i)PHONE|LAPTOP|TABLET|OTHER", message = "Device type must be PHONE, LAPTOP, TABLET or OTHER")
        String deviceType,

        @Size(max = 100, message = "Device model must be at most 100 characters")
        String deviceModel,

        @NotBlank(message = "Issue description is required")
        @Size(min = 10, max = 1000, message = "Issue description must be 10 to 1000 characters")
        String issueDescription) {
}
