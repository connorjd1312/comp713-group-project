package nz.ac.aut.comp713.repair_tickets.live;

import nz.ac.aut.comp713.repair_tickets.event.TicketChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket at /ws/tickets. Same protocol as the Week 10 live lab:
 * the client sends {"type":"subscribe"}, the server registers it and replies {"type":"subscribed"},
 * then the client GETs the current state over REST. Notifications only say what changed;
 * REST stays the source of truth, so a missed message is repaired by the next snapshot.
 */
@Component
public class TicketLiveHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TicketLiveHandler.class);

    private final ObjectMapper json;
    private final Map<String, WebSocketSession> subscribers = new ConcurrentHashMap<>();

    public TicketLiveHandler(ObjectMapper json) {
        this.json = json;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode request;
        try {
            request = json.readTree(message.getPayload());
            if (request == null || !request.isObject()) throw new IllegalArgumentException("Expected an object");
        } catch (RuntimeException e) {
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"Invalid JSON\"}"));
            return;
        }
        if (!"subscribe".equals(request.path("type").asString(""))) {
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"Send {\\\"type\\\":\\\"subscribe\\\"}\"}"));
            return;
        }
        // Register first, then acknowledge: anything committed after this point reaches the client.
        // Sends are serialised per session because broadcasts run on request threads.
        WebSocketSession subscriber = subscribers.computeIfAbsent(session.getId(),
                key -> new ConcurrentWebSocketSessionDecorator(session, 2000, 64 * 1024));
        subscriber.sendMessage(new TextMessage("{\"type\":\"subscribed\"}"));
        log.info("LIVE subscribed session={} subscribers={}", session.getId(), subscribers.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        subscribers.remove(session.getId());
        log.info("LIVE closed session={} code={} subscribers={}", session.getId(), status.getCode(), subscribers.size());
    }

    /** Runs only after the ticket change has committed. */
    @TransactionalEventListener
    public void onTicketChanged(TicketChangedEvent event) {
        String payload = json.writeValueAsString(Map.of(
                "type", "ticketChanged",
                "change", event.change(),
                "ticketId", event.ticketId(),
                "status", event.status().name(),
                "updatedAt", event.updatedAt().toString()));
        for (WebSocketSession session : subscribers.values()) {
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (Exception e) {
                // A dead connection must not stop the other subscribers or fail the REST request.
                subscribers.remove(session.getId(), session);
                log.warn("LIVE dropped session={}: {}", session.getId(), e.getMessage());
            }
        }
        log.info("LIVE sent {} ticket={} to {} subscriber(s)", event.change(), event.ticketId(), subscribers.size());
    }

    int subscriberCount() {
        return subscribers.size();
    }
}
