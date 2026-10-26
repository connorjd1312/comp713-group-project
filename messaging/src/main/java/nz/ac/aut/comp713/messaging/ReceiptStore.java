package nz.ac.aut.comp713.messaging;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consumer-owned data. No access to the backend's repair database. */
public class ReceiptStore implements AutoCloseable {
    private final String url;
    private final Connection keeper;
    private final ObjectMapper json = new ObjectMapper();

    public ReceiptStore(String url) throws SQLException {
        this.url = url;
        // Keep the consumer database open between deliveries. Otherwise AUTO_SERVER would
        // repeatedly stop and restart for every receipt and inspection connection.
        this.keeper = connect();
        try (var statement = keeper.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS receipts (
                      event_id VARCHAR(100) PRIMARY KEY, ticket_id BIGINT NOT NULL,
                      change_type VARCHAR(50) NOT NULL, ticket_status VARCHAR(30) NOT NULL,
                      payload CLOB NOT NULL, received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
                    """);
        }
    }

    private Connection connect() throws SQLException { return DriverManager.getConnection(url, "sa", ""); }

    @Override
    public void close() throws SQLException { keeper.close(); }

    public boolean save(String payload) throws SQLException {
        JsonNode event = json.readTree(payload);
        if (event == null || !event.isObject() || event.path("eventId").asString("").isBlank()
                || event.path("ticketId").asLong(0) <= 0 || event.path("change").asString("").isBlank()
                || event.path("status").asString("").isBlank()) {
            throw new IllegalArgumentException("Invalid repair event");
        }
        try (var connection = connect()) {
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("""
                    INSERT INTO receipts(event_id, ticket_id, change_type, ticket_status, payload)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                insert.setString(1, event.path("eventId").asString());
                insert.setLong(2, event.path("ticketId").asLong());
                insert.setString(3, event.path("change").asString());
                insert.setString(4, event.path("status").asString());
                insert.setString(5, payload);
                insert.executeUpdate();
                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                if ("23505".equals(ex.getSQLState())) return false;
                throw ex;
            }
        }
    }

    public List<Map<String, Object>> list() throws SQLException {
        var rows = new ArrayList<Map<String, Object>>();
        try (var connection = connect(); var statement = connection.createStatement();
             var results = statement.executeQuery("SELECT * FROM receipts ORDER BY received_at, event_id")) {
            while (results.next()) {
                var row = new LinkedHashMap<String, Object>();
                row.put("eventId", results.getString("event_id"));
                row.put("ticketId", results.getLong("ticket_id"));
                row.put("change", results.getString("change_type"));
                row.put("status", results.getString("ticket_status"));
                row.put("payload", results.getString("payload"));
                row.put("receivedAt", results.getTimestamp("received_at").toString());
                rows.add(row);
            }
        }
        return rows;
    }
}
