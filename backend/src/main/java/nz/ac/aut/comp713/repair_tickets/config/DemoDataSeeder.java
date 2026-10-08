package nz.ac.aut.comp713.repair_tickets.config;

import nz.ac.aut.comp713.repair_tickets.dto.TicketResponse;
import nz.ac.aut.comp713.repair_tickets.model.TicketStatus;
import nz.ac.aut.comp713.repair_tickets.repository.RepairTicketRepository;
import nz.ac.aut.comp713.repair_tickets.service.TicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** 資料庫是空的時候，啟動時自動放入 3 張示範工單，方便測試和錄影。 */
@Component
public class DemoDataSeeder implements CommandLineRunner {

    private final TicketService service;
    private final RepairTicketRepository tickets;
    private final boolean enabled;

    public DemoDataSeeder(TicketService service, RepairTicketRepository tickets,
                          @Value("${app.seed-demo-data:true}") boolean enabled) {
        this.service = service;
        this.tickets = tickets;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled || tickets.count() > 0) {
            return;
        }
        service.createTicket("Alice Chen", "alice@example.com", "021 111 1111",
                "PHONE", "iPhone 13", "Cracked screen after a drop, touch still works.");

        TicketResponse ben = service.createTicket("Ben Taylor", "ben@example.com", "022 222 2222",
                "LAPTOP", "MacBook Air M1", "Battery drains from full to empty in about one hour.");
        service.updateStatus(ben.id(), TicketStatus.DIAGNOSING, "Running battery health check");

        TicketResponse chloe = service.createTicket("Chloe Wong", "chloe@example.com", null,
                "TABLET", "iPad 9th gen", "Charging port is loose and only charges at an angle.");
        service.updateStatus(chloe.id(), TicketStatus.DIAGNOSING, "Inspecting charging port");
        service.updateStatus(chloe.id(), TicketStatus.REPAIRING, "Replacing charging port");
        service.updateStatus(chloe.id(), TicketStatus.READY, "Port replaced and tested, ready for pickup");
    }
}
