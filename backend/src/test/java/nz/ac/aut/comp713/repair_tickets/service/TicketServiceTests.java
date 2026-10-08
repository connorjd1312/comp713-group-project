package nz.ac.aut.comp713.repair_tickets.service;

import nz.ac.aut.comp713.repair_tickets.dto.TicketResponse;
import nz.ac.aut.comp713.repair_tickets.exception.BusinessRuleException;
import nz.ac.aut.comp713.repair_tickets.exception.NotFoundException;
import nz.ac.aut.comp713.repair_tickets.model.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional   // 每個測試結束後自動還原資料，測試之間互不影響
class TicketServiceTests {

    @Autowired
    TicketService service;

    private TicketResponse newTicket(String email) {
        return service.createTicket("Wayne", email, "0211234567", "phone", "iPhone 13", "Cracked screen");
    }

    @Test
    void newTicketStartsAsReceived() {
        TicketResponse t = newTicket("wayne@example.com");

        assertThat(t.id()).isNotNull();
        assertThat(t.status()).isEqualTo(TicketStatus.RECEIVED);
        assertThat(t.deviceType()).isEqualTo("PHONE");
        assertThat(t.history()).isEmpty();
    }

    @Test
    void sameEmailReusesExistingCustomer() {
        newTicket("wayne@example.com");
        newTicket("WAYNE@example.com");

        assertThat(service.listTickets(null, "wayne@example.com")).hasSize(2);
    }

    @Test
    void statusFollowsWorkflowAndRecordsHistory() {
        Long id = newTicket("a@example.com").id();

        service.updateStatus(id, TicketStatus.DIAGNOSING, "Checking screen");
        TicketResponse t = service.updateStatus(id, TicketStatus.REPAIRING, "Replacing screen");

        assertThat(t.status()).isEqualTo(TicketStatus.REPAIRING);
        assertThat(t.history()).hasSize(2);
        assertThat(t.history().get(1).from()).isEqualTo(TicketStatus.DIAGNOSING);
        assertThat(t.history().get(1).to()).isEqualTo(TicketStatus.REPAIRING);
    }

    @Test
    void skippingStepsIsRejected() {
        Long id = newTicket("b@example.com").id();

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> service.updateStatus(id, TicketStatus.READY, null));

        assertThat(e.getCode()).isEqualTo("INVALID_TRANSITION");
        assertThat(service.getTicket(id).status()).isEqualTo(TicketStatus.RECEIVED);
    }

    @Test
    void canCancelOnlyWhileReceived() {
        Long first = newTicket("c@example.com").id();
        assertThat(service.cancelTicket(first).status()).isEqualTo(TicketStatus.CANCELLED);

        Long second = newTicket("d@example.com").id();
        service.updateStatus(second, TicketStatus.DIAGNOSING, null);
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> service.cancelTicket(second));
        assertThat(e.getCode()).isEqualTo("CANNOT_CANCEL");
    }

    @Test
    void unknownTicketThrowsNotFound() {
        assertThrows(NotFoundException.class, () -> service.getTicket(99999L));
    }

    @Test
    void listCanFilterByStatus() {
        Long id = newTicket("e@example.com").id();
        newTicket("f@example.com");
        service.updateStatus(id, TicketStatus.DIAGNOSING, null);

        assertThat(service.listTickets(TicketStatus.DIAGNOSING, null))
                .extracting(TicketResponse::id)
                .contains(id)
                .allSatisfy(x -> assertThat(service.getTicket(x).status()).isEqualTo(TicketStatus.DIAGNOSING));
    }
}
