package nz.ac.aut.comp713.repair_tickets.event;

import nz.ac.aut.comp713.repair_tickets.model.QuoteStatus;
import nz.ac.aut.comp713.repair_tickets.model.TicketStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event raised by TicketService whenever a ticket changes.
 * Listeners only see it after the database transaction has committed, so they never announce a change that rolled back.
 * eventId is stable for this one change, so a consumer can recognise a repeated delivery.
 */
public record TicketChangedEvent(
        String eventId,
        String change,
        Long ticketId,
        String customerEmail,
        TicketStatus status,
        QuoteStatus quoteStatus,
        LocalDateTime updatedAt) {

    public static TicketChangedEvent of(String change, Long ticketId, String customerEmail,
                                        TicketStatus status, QuoteStatus quoteStatus, LocalDateTime updatedAt) {
        return new TicketChangedEvent(UUID.randomUUID().toString(), change, ticketId, customerEmail,
                status, quoteStatus, updatedAt);
    }
}
