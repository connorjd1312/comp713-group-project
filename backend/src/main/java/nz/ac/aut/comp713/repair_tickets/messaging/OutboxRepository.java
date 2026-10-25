package nz.ac.aut.comp713.repair_tickets.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findTop10BySentAtIsNullOrderByCreatedAtAsc();
    long countBySentAtIsNull();
    long countBySentAtIsNotNull();
}
