package nz.ac.aut.comp713.messaging;

import jakarta.jms.*;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

/** Separate broker and consumer processes, using the transacted JMS pattern from Week 10. */
public final class MessagingMain {
    private MessagingMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java -jar repair-messaging.jar broker|consume|receipts|publish <event.json> [--once] [--crash-after-save]");
            return;
        }
        String brokerUrl = System.getenv().getOrDefault("BROKER_URL", "tcp://127.0.0.1:61616");
        String queue = System.getenv().getOrDefault("BROKER_QUEUE", "repair.events");
        Path data = Path.of(System.getenv().getOrDefault("REPAIR_DATA_DIR", "data")).toAbsolutePath();
        Files.createDirectories(data);
        switch (args[0]) {
            case "broker" -> {
                var server = broker(data.resolve("broker"), brokerUrl, queue);
                server.start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try { server.stop(); } catch (Exception ex) { ex.printStackTrace(); }
                }));
                System.out.println("BROKER READY " + brokerUrl + " queue=" + queue);
                new CountDownLatch(1).await();
            }
            case "receipts" -> {
                try (var receipts = store(data)) {
                    System.out.println(new ObjectMapper().writeValueAsString(receipts.list()));
                }
            }
            case "publish" -> {
                if (args.length < 2) throw new IllegalArgumentException("Supply an event JSON file");
                try (var factory = new ActiveMQConnectionFactory(brokerUrl);
                     var context = factory.createContext(JMSContext.SESSION_TRANSACTED)) {
                    context.createProducer().setDeliveryMode(DeliveryMode.PERSISTENT)
                            .send(context.createQueue(queue), Files.readString(Path.of(args[1])));
                    context.commit();
                    System.out.println("PUBLISHED");
                }
            }
            case "consume" -> {
                try (var receipts = store(data)) {
                    consume(brokerUrl, queue, receipts, Arrays.asList(args).contains("--once"),
                            Arrays.asList(args).contains("--crash-after-save"));
                }
            }
            default -> throw new IllegalArgumentException("Unknown command " + args[0]);
        }
    }

    private static ReceiptStore store(Path data) throws Exception {
        // AUTO_SERVER permits the receipts inspection command while the consumer is running.
        return new ReceiptStore("jdbc:h2:file:" + data.resolve("receipts").toString().replace('\\', '/')
                + ";AUTO_SERVER=TRUE;WRITE_DELAY=0");
    }

    public static EmbeddedActiveMQ broker(Path directory, String url, String queue) throws Exception {
        var config = new ConfigurationImpl().setPersistenceEnabled(true).setSecurityEnabled(false)
                // A small local demo needs a fixed reserve rather than 10% of the whole host disk.
                .setMinDiskFree(256L * 1024 * 1024)
                .setJournalType(JournalType.NIO)
                .setJournalDirectory(directory.resolve("journal").toString())
                .setBindingsDirectory(directory.resolve("bindings").toString())
                .setPagingDirectory(directory.resolve("paging").toString())
                .setLargeMessagesDirectory(directory.resolve("large-messages").toString())
                .addAcceptorConfiguration("tcp", url)
                .addQueueConfiguration(QueueConfiguration.of(queue).setRoutingType(RoutingType.ANYCAST).setDurable(true));
        return new EmbeddedActiveMQ().setConfiguration(config);
    }

    private static void consume(String url, String queue, ReceiptStore store, boolean once, boolean crash) throws Exception {
        do {
            try (var factory = new ActiveMQConnectionFactory(url)) {
                factory.setInitialConnectAttempts(0);
                factory.setReconnectAttempts(0);
                factory.setConsumerWindowSize(0);
                factory.setCallTimeout(2000);
                try (var context = factory.createContext(JMSContext.SESSION_TRANSACTED);
                     var consumer = context.createConsumer(context.createQueue(queue))) {
                    System.out.println("CONSUMER READY " + queue);
                    do {
                        Message message = consumer.receive(2000);
                        if (message == null) continue;
                        try {
                            boolean saved = store.save(message.getBody(String.class));
                            System.out.println((saved ? "DB COMMITTED" : "DUPLICATE SKIPPED")
                                    + " redelivered=" + message.getJMSRedelivered());
                            if (crash) {
                                System.out.flush();
                                Runtime.getRuntime().halt(23);
                            }
                            context.commit();
                            System.out.println("JMS COMMITTED");
                            if (once) return;
                        } catch (Exception ex) {
                            context.rollback();
                            System.err.println("PROCESSING RETRY " + ex.getMessage());
                            Thread.sleep(1000);
                        }
                    } while (!Thread.currentThread().isInterrupted());
                }
            } catch (JMSRuntimeException ex) {
                if (once) throw ex;
                System.err.println("CONSUMER RECONNECT " + ex.getClass().getSimpleName());
                Thread.sleep(2000);
            }
        } while (!Thread.currentThread().isInterrupted());
    }
}
