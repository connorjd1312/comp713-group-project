package nz.ac.aut.comp713.messaging;

import jakarta.jms.*;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.ServerSocket;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryTests {
    @TempDir Path data;
    private static final String EVENT = """
            {"eventId":"repeat-1","ticketId":42,"change":"QUOTE_ISSUED","status":"DIAGNOSING"}
            """;

    @Test
    void persistentQueueSurvivesRestartAndRedeliveryDoesNotDuplicateReceipt() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        String url = "tcp://127.0.0.1:" + port;
        var broker = MessagingMain.broker(data.resolve("broker"), url, "repair.events");
        broker.start();
        try {
            try (var factory = new ActiveMQConnectionFactory(url);
                 var context = factory.createContext(JMSContext.SESSION_TRANSACTED)) {
                context.createProducer().setDeliveryMode(DeliveryMode.PERSISTENT)
                        .send(context.createQueue("repair.events"), EVENT);
                context.commit();
            }
            broker.stop();
            broker = MessagingMain.broker(data.resolve("broker"), url, "repair.events");
            broker.start();
            try (var store = new ReceiptStore("jdbc:h2:file:" + data.resolve("receipts"));
                 var factory = new ActiveMQConnectionFactory(url);
                 var context = factory.createContext(JMSContext.SESSION_TRANSACTED);
                 var consumer = context.createConsumer(context.createQueue("repair.events"))) {
                var first = consumer.receive(5000);
                assertNotNull(first, "persistent message survives broker restart");
                assertTrue(store.save(first.getBody(String.class)));
                context.rollback(); // same boundary as a crash after DB save and before JMS commit
                var repeated = consumer.receive(5000);
                assertNotNull(repeated);
                assertTrue(repeated.getJMSRedelivered());
                assertFalse(store.save(repeated.getBody(String.class)));
                context.commit();
                assertEquals(1, store.list().size());
                assertNull(consumer.receive(300));
            }
        } finally { broker.stop(); }
    }

    @Test
    void invalidEventDoesNotCreateAReceipt() throws Exception {
        try (var store = new ReceiptStore("jdbc:h2:mem:invalid;DB_CLOSE_DELAY=-1")) {
            assertThrows(IllegalArgumentException.class, () -> store.save("{}"));
            assertTrue(store.list().isEmpty());
        }
    }
}
