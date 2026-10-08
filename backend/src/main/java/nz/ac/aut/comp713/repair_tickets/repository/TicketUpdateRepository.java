package nz.ac.aut.comp713.repair_tickets.repository;

import nz.ac.aut.comp713.repair_tickets.model.TicketUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketUpdateRepository extends JpaRepository<TicketUpdate, Long> {
    List<TicketUpdate> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
