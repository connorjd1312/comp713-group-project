package nz.ac.aut.comp713.repair_tickets.messaging;

import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSContext;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository outbox;
    private final String brokerUrl;
    private final String queue;

    public OutboxPublisher(OutboxRepository outbox,
                           @Value("${app.messaging.broker-url}") String brokerUrl,
                           @Value("${app.messaging.queue:repair.events}") String queue) {
        this.outbox = outbox;
        this.brokerUrl = brokerUrl;
        this.queue = queue;
    }

    @Scheduled(fixedDelayString = "${app.messaging.retry-ms:2000}")
    @Transactional
    public void publishPending() {
        var pending = outbox.findTop10BySentAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) return;
        try (var factory = new ActiveMQConnectionFactory(brokerUrl)) {
            factory.setInitialConnectAttempts(0);
            factory.setReconnectAttempts(0);
            factory.setCallTimeout(2000);
            factory.setCallFailoverTimeout(2000);
            try (JMSContext context = factory.createContext(JMSContext.SESSION_TRANSACTED)) {
                var destination = context.createQueue(queue);
                for (OutboxEvent event : pending) {
                    event.attempted();
                    context.createProducer().setDeliveryMode(DeliveryMode.PERSISTENT)
                            .setProperty("eventId", event.getEventId()).send(destination, event.getPayload());
                    context.commit();
                    event.delivered();
                    log.info("OUTBOX SENT eventId={}", event.getEventId());
                }
            }
        } catch (RuntimeException ex) {
            // Keep pending rows. A crash after send but before DB commit may repeat delivery.
            log.warn("OUTBOX RETRY: broker unavailable or send failed; pending events retained ({})",
                    ex.getClass().getSimpleName());
        }
    }
}
