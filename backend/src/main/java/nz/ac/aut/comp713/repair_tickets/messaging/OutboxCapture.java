package nz.ac.aut.comp713.repair_tickets.messaging;

import nz.ac.aut.comp713.repair_tickets.event.TicketChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxCapture {
    private final OutboxRepository outbox;
    private final ObjectMapper json;

    public OutboxCapture(OutboxRepository outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    // Synchronous listener: both the ticket and its event commit, or neither does.
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(TicketChangedEvent event) {
        outbox.save(new OutboxEvent(event.eventId(), json.writeValueAsString(event)));
    }
}
