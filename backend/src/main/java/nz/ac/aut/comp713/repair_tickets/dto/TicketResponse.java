package nz.ac.aut.comp713.repair_tickets.dto;

import nz.ac.aut.comp713.repair_tickets.model.RepairTicket;
import nz.ac.aut.comp713.repair_tickets.model.TicketStatus;
import nz.ac.aut.comp713.repair_tickets.model.TicketUpdate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回傳給前端的工單資料（DTO）。
 * 不直接回傳 Entity，避免把資料庫結構整個暴露出去，也避免 JSON 循環參照。
 */
public record TicketResponse(
        Long id,
        String customerName,
        String customerEmail,
        String deviceType,
        String deviceModel,
        String issueDescription,
        TicketStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<StatusChange> history) {

    public record StatusChange(TicketStatus from, TicketStatus to, String note, LocalDateTime at) {
        static StatusChange of(TicketUpdate u) {
            return new StatusChange(u.getFromStatus(), u.getToStatus(), u.getNote(), u.getCreatedAt());
        }
    }

    public static TicketResponse from(RepairTicket t) {
        return new TicketResponse(
                t.getId(),
                t.getCustomer().getName(),
                t.getCustomer().getEmail(),
                t.getDeviceType(),
                t.getDeviceModel(),
                t.getIssueDescription(),
                t.getStatus(),
                t.getCreatedAt(),
                t.getUpdatedAt(),
                t.getUpdates().stream().map(StatusChange::of).toList());
    }
}
