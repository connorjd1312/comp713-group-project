package nz.ac.aut.comp713.repair_tickets.live;

import nz.ac.aut.comp713.repair_tickets.dto.TicketResponse;
import nz.ac.aut.comp713.repair_tickets.exception.BusinessRuleException;
import nz.ac.aut.comp713.repair_tickets.model.TicketStatus;
import nz.ac.aut.comp713.repair_tickets.service.TicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the real server on a random port and connects a real WebSocket client, like an open technician page.
 * Not @Transactional: the service calls must commit, because notifications are only sent after commit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TicketLiveHandlerTests {

    @Value("${local.server.port}")
    int port;

    @Autowired
    TicketService service;

    final BlockingQueue<String> received = new LinkedBlockingQueue<>();
    WebSocket socket;

    @BeforeEach
    void connect() throws Exception {
        socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/tickets"), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        received.add(data.toString());
                        ws.request(1);
                        return null;
                    }
                })
                .get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void close() {
        socket.abort();
    }

    private String next() throws InterruptedException {
        String message = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(message, "no WebSocket message within 5 s");
        return message;
    }

    private void subscribe() throws Exception {
        socket.sendText("{\"type\":\"subscribe\"}", true).get(5, TimeUnit.SECONDS);
        assertTrue(next().contains("\"subscribed\""));
    }

    @Test
    void subscriberIsNotifiedWhenAnotherUserChangesATicket() throws Exception {
        subscribe();
        TicketResponse ticket = service.createTicket("Live Test", "live@example.com", null,
                "PHONE", "Pixel 8", "Screen flickers when the brightness is low");
        assertTrue(next().contains("\"change\":\"CREATED\""));

        service.updateStatus(ticket.id(), TicketStatus.DIAGNOSING, "Checking display cable");
        String notice = next();
        assertTrue(notice.contains("\"ticketId\":" + ticket.id()), notice);
        assertTrue(notice.contains("\"status\":\"DIAGNOSING\""), notice);
    }

    @Test
    void rejectedChangeIsNotBroadcast() throws Exception {
        subscribe();
        TicketResponse ticket = service.createTicket("Live Test", "live2@example.com", null,
                "LAPTOP", null, "Keyboard keys stick after a coffee spill");
        next(); // CREATED

        // RECEIVED -> READY is an invalid jump: the transaction rolls back, so nothing may be announced.
        assertThrows(BusinessRuleException.class,
                () -> service.updateStatus(ticket.id(), TicketStatus.READY, null));
        assertNull(received.poll(1, TimeUnit.SECONDS));
    }

    @Test
    void unsubscribedClientReceivesNothingAndBadRequestsGetAnError() throws Exception {
        service.createTicket("Live Test", "live3@example.com", null,
                "TABLET", null, "Battery swells and the case is lifting");
        assertNull(received.poll(1, TimeUnit.SECONDS));

        socket.sendText("{\"type\":\"hello\"}", true).get(5, TimeUnit.SECONDS);
        assertTrue(next().contains("\"error\""));
    }
}
