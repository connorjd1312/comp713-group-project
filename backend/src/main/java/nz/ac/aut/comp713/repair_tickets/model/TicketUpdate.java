package nz.ac.aut.comp713.repair_tickets.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_updates")
public class TicketUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 多筆紀錄 -> 一張工單（外鍵 ticket_id）
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private RepairTicket ticket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus toStatus;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TicketUpdate() { }

    TicketUpdate(RepairTicket ticket, TicketStatus fromStatus, TicketStatus toStatus, String note) {
        this.ticket = ticket;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public TicketStatus getFromStatus() { return fromStatus; }
    public TicketStatus getToStatus() { return toStatus; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
