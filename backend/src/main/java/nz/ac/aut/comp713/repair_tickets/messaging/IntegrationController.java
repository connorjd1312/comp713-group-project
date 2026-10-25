package nz.ac.aut.comp713.repair_tickets.messaging;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class IntegrationController {
    private final OutboxRepository outbox;

    public IntegrationController(OutboxRepository outbox) { this.outbox = outbox; }

    @GetMapping("/api/integration/status")
    public Map<String, Long> status() {
        return Map.of("pending", outbox.countBySentAtIsNull(), "published", outbox.countBySentAtIsNotNull());
    }
}
