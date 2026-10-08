package nz.ac.aut.comp713.repair_tickets.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import nz.ac.aut.comp713.repair_tickets.model.TicketStatus;

/** 技師更新狀態時傳來的 JSON，例如 {"status":"DIAGNOSING","note":"Checking battery"}。 */
public record UpdateStatusRequest(

        @NotNull(message = "Status is required")
        TicketStatus status,

        @Size(max = 500, message = "Note must be at most 500 characters")
        String note) {
}
