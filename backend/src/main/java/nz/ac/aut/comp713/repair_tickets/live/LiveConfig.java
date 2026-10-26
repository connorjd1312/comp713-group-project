package nz.ac.aut.comp713.repair_tickets.live;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the live-update WebSocket on the same port as the REST API (8080). */
@Configuration
@EnableWebSocket
public class LiveConfig implements WebSocketConfigurer {

    private final TicketLiveHandler handler;

    public LiveConfig(TicketLiveHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/tickets");
    }
}
