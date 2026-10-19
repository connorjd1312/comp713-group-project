package nz.ac.aut.comp713.repair_tickets.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Technician issues a quote, e.g. {"amount":189.00,"description":"Replace screen assembly"}. */
public record CreateQuoteRequest(

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @DecimalMax(value = "99999.99", message = "Amount must be less than 100000")
        @Digits(integer = 5, fraction = 2, message = "Amount may have at most 2 decimal places")
        BigDecimal amount,

        @NotBlank(message = "Description is required")
        @Size(max = 500, message = "Description must be at most 500 characters")
        String description) {
}
