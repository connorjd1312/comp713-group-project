package nz.ac.aut.comp713.repair_tickets.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Customer approves or declines the current quote, e.g.
 * {"customerEmail":"ben@example.com","approved":true,"comment":"Go ahead"}.
 * The email must match the ticket's customer.
 */
public record QuoteDecisionRequest(

        @NotBlank(message = "Customer email is required")
        @Email(message = "Email format is invalid")
        String customerEmail,

        @NotNull(message = "Approved must be true or false")
        Boolean approved,

        @Size(max = 500, message = "Comment must be at most 500 characters")
        String comment) {
}
