package nz.ac.aut.comp713.repair_tickets.service;

import nz.ac.aut.comp713.repair_tickets.dto.TicketResponse;
import nz.ac.aut.comp713.repair_tickets.exception.BusinessRuleException;
import nz.ac.aut.comp713.repair_tickets.exception.NotFoundException;
import nz.ac.aut.comp713.repair_tickets.model.Customer;
import nz.ac.aut.comp713.repair_tickets.model.RepairTicket;
import nz.ac.aut.comp713.repair_tickets.model.TicketStatus;
import nz.ac.aut.comp713.repair_tickets.repository.CustomerRepository;
import nz.ac.aut.comp713.repair_tickets.repository.RepairTicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商業邏輯層：所有「工單可以怎麼變化」的規則都集中在這裡。
 * Controller 只負責收發 HTTP，Repository 只負責存取資料庫。
 */
@Service
public class TicketService {

    private final CustomerRepository customers;
    private final RepairTicketRepository tickets;

    public TicketService(CustomerRepository customers, RepairTicketRepository tickets) {
        this.customers = customers;
        this.tickets = tickets;
    }

    /** 建立工單：email 已存在就沿用該客戶，否則建立新客戶。新工單狀態一律是 RECEIVED。 */
    @Transactional
    public TicketResponse createTicket(String name, String email, String phone,
                                       String deviceType, String deviceModel, String issueDescription) {
        String normalisedEmail = email.trim().toLowerCase();
        Customer customer = customers.findByEmailIgnoreCase(normalisedEmail)
                .orElseGet(() -> customers.save(new Customer(name.trim(), normalisedEmail, phone)));

        RepairTicket ticket = new RepairTicket(customer, deviceType.trim().toUpperCase(),
                deviceModel, issueDescription.trim());
        return TicketResponse.from(tickets.save(ticket));
    }

    /** 列出工單，可依狀態及／或客戶 email 篩選，新的在前。 */
    @Transactional(readOnly = true)
    public List<TicketResponse> listTickets(TicketStatus status, String email) {
        boolean hasEmail = email != null && !email.isBlank();
        List<RepairTicket> result;
        if (status != null) {
            result = tickets.findByStatusOrderByCreatedAtDesc(status);
            if (hasEmail) {
                result = result.stream()
                        .filter(t -> t.getCustomer().getEmail().equalsIgnoreCase(email.trim()))
                        .toList();
            }
        } else if (hasEmail) {
            result = tickets.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(email.trim());
        } else {
            result = tickets.findAllByOrderByCreatedAtDesc();
        }
        return result.stream().map(TicketResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicket(Long id) {
        return TicketResponse.from(findTicket(id));
    }

    /**
     * 技師更新狀態。不合法的跳轉（例如 RECEIVED -> READY）會被拒絕。
     * 更新狀態和寫入歷史紀錄在同一個交易裡：要嘛兩個都成功，要嘛都不發生。
     */
    @Transactional
    public TicketResponse updateStatus(Long id, TicketStatus next, String note) {
        RepairTicket ticket = findTicket(id);
        if (!ticket.getStatus().canMoveTo(next)) {
            throw new BusinessRuleException("INVALID_TRANSITION",
                    "Cannot move ticket " + id + " from " + ticket.getStatus() + " to " + next);
        }
        ticket.changeStatus(next, note);
        return TicketResponse.from(tickets.saveAndFlush(ticket));
    }

    /** 取消工單：只有還在 RECEIVED（尚未開始檢測）時可以取消。保留紀錄，不真的刪除。 */
    @Transactional
    public TicketResponse cancelTicket(Long id) {
        RepairTicket ticket = findTicket(id);
        if (ticket.getStatus() != TicketStatus.RECEIVED) {
            throw new BusinessRuleException("CANNOT_CANCEL",
                    "Ticket " + id + " is already " + ticket.getStatus() + " and can no longer be cancelled");
        }
        ticket.changeStatus(TicketStatus.CANCELLED, "Cancelled by customer");
        return TicketResponse.from(tickets.saveAndFlush(ticket));
    }

    private RepairTicket findTicket(Long id) {
        return tickets.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket " + id + " not found"));
    }
}
