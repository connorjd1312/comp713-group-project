package nz.ac.aut.comp713.repair_tickets.dto;

import nz.ac.aut.comp713.repair_tickets.model.Quote;
import nz.ac.aut.comp713.repair_tickets.model.QuoteStatus;
import nz.ac.aut.comp713.repair_tickets.model.RepairTicket;
import nz.ac.aut.comp713.repair_tickets.model.TicketStatus;
import nz.ac.aut.comp713.repair_tickets.model.TicketUpdate;

import java.math.BigDecimal;
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
        QuoteView quote,
        List<StatusChange> history) {

    public record StatusChange(TicketStatus from, TicketStatus to, String note, LocalDateTime at) {
        static StatusChange of(TicketUpdate u) {
            return new StatusChange(u.getFromStatus(), u.getToStatus(), u.getNote(), u.getCreatedAt());
        }
    }

    /** The current (newest) quote; null when none has been issued. */
    public record QuoteView(Long id, BigDecimal amount, String description, QuoteStatus status,
                            String customerComment, LocalDateTime createdAt, LocalDateTime respondedAt) {
        static QuoteView of(Quote q) {
            return q == null ? null : new QuoteView(q.getId(), q.getAmount(), q.getDescription(), q.getStatus(),
                    q.getCustomerComment(), q.getCreatedAt(), q.getRespondedAt());
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
                QuoteView.of(t.currentQuote()),
                t.getUpdates().stream().map(StatusChange::of).toList());
    }
}
