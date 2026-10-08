package nz.ac.aut.comp713.repair_tickets.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "repair_tickets")
public class RepairTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 多張工單 -> 一位客戶（外鍵 customer_id）
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, length = 30)
    private String deviceType;      // 例如 PHONE、LAPTOP

    @Column(length = 100)
    private String deviceModel;     // 例如 iPhone 13

    @Column(nullable = false, length = 1000)
    private String issueDescription;

    @Enumerated(EnumType.STRING)    // 存成文字 "RECEIVED"，不是數字 0
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 一張工單 -> 多筆狀態紀錄
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<TicketUpdate> updates = new ArrayList<>();

    protected RepairTicket() { }

    public RepairTicket(Customer customer, String deviceType, String deviceModel, String issueDescription) {
        this.customer = customer;
        this.deviceType = deviceType;
        this.deviceModel = deviceModel;
        this.issueDescription = issueDescription;
        this.status = TicketStatus.RECEIVED;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** 改狀態的唯一入口：同時留下一筆歷史紀錄。 */
    public void changeStatus(TicketStatus next, String note) {
        updates.add(new TicketUpdate(this, status, next, note));
        status = next;
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public String getDeviceType() { return deviceType; }
    public String getDeviceModel() { return deviceModel; }
    public String getIssueDescription() { return issueDescription; }
    public TicketStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<TicketUpdate> getUpdates() { return updates; }
}
