package nz.ac.aut.comp713.repair_tickets.service;

import nz.ac.aut.comp713.repair_tickets.dto.TicketResponse;
import nz.ac.aut.comp713.repair_tickets.exception.BusinessRuleException;
import nz.ac.aut.comp713.repair_tickets.exception.ForbiddenException;
import nz.ac.aut.comp713.repair_tickets.exception.NotFoundException;
import nz.ac.aut.comp713.repair_tickets.model.QuoteStatus;
import nz.ac.aut.comp713.repair_tickets.model.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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
        service.createQuote(id, new BigDecimal("150.00"), "Replace screen");
        service.decideQuote(id, "a@example.com", true, null);
        TicketResponse t = service.updateStatus(id, TicketStatus.REPAIRING, "Replacing screen");

        assertThat(t.status()).isEqualTo(TicketStatus.REPAIRING);
        // DIAGNOSING, quote issued, quote approved, REPAIRING
        assertThat(t.history()).hasSize(4);
        assertThat(t.history().get(3).from()).isEqualTo(TicketStatus.DIAGNOSING);
        assertThat(t.history().get(3).to()).isEqualTo(TicketStatus.REPAIRING);
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

    // ---- Assessment 3: quote approval workflow (technician -> customer -> technician) ----

    private Long diagnosingTicket(String email) {
        Long id = newTicket(email).id();
        service.updateStatus(id, TicketStatus.DIAGNOSING, null);
        return id;
    }

    @Test
    void repairCannotStartWithoutApprovedQuote() {
        Long id = diagnosingTicket("q1@example.com");

        BusinessRuleException noQuote = assertThrows(BusinessRuleException.class,
                () -> service.updateStatus(id, TicketStatus.REPAIRING, null));
        assertThat(noQuote.getCode()).isEqualTo("QUOTE_NOT_APPROVED");

        service.createQuote(id, new BigDecimal("99.50"), "Replace battery");
        BusinessRuleException pending = assertThrows(BusinessRuleException.class,
                () -> service.updateStatus(id, TicketStatus.REPAIRING, null));
        assertThat(pending.getCode()).isEqualTo("QUOTE_NOT_APPROVED");
        assertThat(service.getTicket(id).status()).isEqualTo(TicketStatus.DIAGNOSING);
    }

    @Test
    void customerApprovalUnlocksRepair() {
        Long id = diagnosingTicket("q2@example.com");
        service.createQuote(id, new BigDecimal("99.50"), "Replace battery");

        TicketResponse approved = service.decideQuote(id, "Q2@example.com", true, "Go ahead");

        assertThat(approved.quote().status()).isEqualTo(QuoteStatus.APPROVED);
        assertThat(approved.quote().respondedAt()).isNotNull();
        assertThat(service.updateStatus(id, TicketStatus.REPAIRING, null).status())
                .isEqualTo(TicketStatus.REPAIRING);
    }

    @Test
    void onlyTheTicketOwnerCanDecide() {
        Long id = diagnosingTicket("owner@example.com");
        service.createQuote(id, new BigDecimal("50"), "Clean ports");

        assertThrows(ForbiddenException.class,
                () -> service.decideQuote(id, "someone.else@example.com", true, null));
        assertThat(service.getTicket(id).quote().status()).isEqualTo(QuoteStatus.PENDING);
    }

    @Test
    void quoteRulesRejectInvalidStates() {
        Long received = newTicket("q3@example.com").id();
        assertThat(assertThrows(BusinessRuleException.class,
                () -> service.createQuote(received, BigDecimal.TEN, "Too early")).getCode())
                .isEqualTo("QUOTE_NOT_ALLOWED");

        Long id = diagnosingTicket("q4@example.com");
        assertThat(assertThrows(BusinessRuleException.class,
                () -> service.decideQuote(id, "q4@example.com", true, null)).getCode())
                .isEqualTo("NO_PENDING_QUOTE");

        service.createQuote(id, BigDecimal.TEN, "First quote");
        assertThat(assertThrows(BusinessRuleException.class,
                () -> service.createQuote(id, BigDecimal.ONE, "Second quote")).getCode())
                .isEqualTo("QUOTE_EXISTS");
    }

    @Test
    void declinedQuoteAllowsRevisedQuoteOrReturnUnrepaired() {
        Long id = diagnosingTicket("q5@example.com");
        service.createQuote(id, new BigDecimal("300"), "Replace logic board");
        assertThat(assertThrows(BusinessRuleException.class,
                () -> service.updateStatus(id, TicketStatus.CANCELLED, null)).getCode())
                .isEqualTo("QUOTE_NOT_DECLINED");

        service.decideQuote(id, "q5@example.com", false, "Too expensive");
        TicketResponse revised = service.createQuote(id, new BigDecimal("180"), "Repair board component");
        assertThat(revised.quote().status()).isEqualTo(QuoteStatus.PENDING);
        assertThat(revised.quote().amount()).isEqualByComparingTo("180");

        service.decideQuote(id, "q5@example.com", false, "Still too much");
        assertThat(service.updateStatus(id, TicketStatus.CANCELLED, "Returned unrepaired").status())
                .isEqualTo(TicketStatus.CANCELLED);
    }
}
