package nz.ac.aut.comp713.repair_tickets.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A repair price issued by a technician during diagnosis and answered by the customer. */
@Entity
@Table(name = "quotes")
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many quotes -> one ticket (a declined quote can be followed by a revised one)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private RepairTicket ticket;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteStatus status;

    @Column(length = 500)
    private String customerComment;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime respondedAt;

    protected Quote() { }

    Quote(RepairTicket ticket, BigDecimal amount, String description) {
        this.ticket = ticket;
        this.amount = amount;
        this.description = description;
        this.status = QuoteStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    void respond(boolean approved, String comment) {
        status = approved ? QuoteStatus.APPROVED : QuoteStatus.DECLINED;
        customerComment = comment;
        respondedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public QuoteStatus getStatus() { return status; }
    public String getCustomerComment() { return customerComment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
}
