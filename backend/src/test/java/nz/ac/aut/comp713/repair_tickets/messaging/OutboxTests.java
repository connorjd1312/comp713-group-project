package nz.ac.aut.comp713.repair_tickets.messaging;

import nz.ac.aut.comp713.repair_tickets.exception.BusinessRuleException;
import nz.ac.aut.comp713.repair_tickets.model.TicketStatus;
import nz.ac.aut.comp713.repair_tickets.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OutboxTests {
    @Autowired TicketService service;
    @Autowired OutboxRepository outbox;

    @Test
    void changesPersistEventsAndRejectedChangesDoNot() {
        var ticket = service.createTicket("Outbox", "outbox@example.com", null,
                "PHONE", null, "The battery is not accepting any charge");
        long before = outbox.count();
        assertTrue(outbox.findAll().stream().anyMatch(e -> e.getPayload().contains("outbox@example.com")));
        assertThrows(BusinessRuleException.class,
                () -> service.updateStatus(ticket.id(), TicketStatus.READY, null));
        assertEquals(before, outbox.count());
        var updated = service.updateStatus(ticket.id(), TicketStatus.DIAGNOSING, "Inspecting");
        assertTrue(updated.version() > ticket.version());
        assertEquals(before + 1, outbox.count());
    }

    @Test
    void unavailableBrokerKeepsTheEventForRetry() {
        service.createTicket("Offline", "offline-broker@example.com", null,
                "PHONE", null, "The charging connector has broken pins");
        long pending = outbox.countBySentAtIsNull();
        new OutboxPublisher(outbox, "tcp://127.0.0.1:1", "repair.events").publishPending();
        assertEquals(pending, outbox.countBySentAtIsNull());
    }
}
