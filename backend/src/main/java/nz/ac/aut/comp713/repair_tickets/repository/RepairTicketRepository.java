package nz.ac.aut.comp713.repair_tickets.repository;

import nz.ac.aut.comp713.repair_tickets.model.RepairTicket;
import nz.ac.aut.comp713.repair_tickets.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RepairTicketRepository extends JpaRepository<RepairTicket, Long> {
    List<RepairTicket> findByStatusOrderByCreatedAtDesc(TicketStatus status);
    List<RepairTicket> findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(String email);
    List<RepairTicket> findAllByOrderByCreatedAtDesc();
}
