package nz.ac.aut.comp713.repair_tickets.messaging;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Saved in the ticket transaction; delivery can be retried after a process or broker failure. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    private String eventId;
    @Lob
    @Column(nullable = false)
    private String payload;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private int attempts;

    protected OutboxEvent() { }

    public OutboxEvent(String eventId, String payload) {
        this.eventId = eventId;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }

    public String getEventId() { return eventId; }
    public String getPayload() { return payload; }
    public LocalDateTime getSentAt() { return sentAt; }
    public int getAttempts() { return attempts; }
    public void attempted() { attempts++; }
    public void delivered() { sentAt = LocalDateTime.now(); }
}
