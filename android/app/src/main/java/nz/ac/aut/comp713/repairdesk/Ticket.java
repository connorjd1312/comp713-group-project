package nz.ac.aut.comp713.repairdesk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** A repair ticket as returned by GET /api/tickets/{id}. Parsed from the server's TicketResponse JSON. */
public final class Ticket {

    public static final List<String> STATUS_FLOW =
            List.of("RECEIVED", "DIAGNOSING", "REPAIRING", "READY", "COLLECTED");

    public final long id;
    public final long version;
    public final String customerName;
    public final String customerEmail;
    public final String deviceType;
    public final String deviceModel;
    public final String issueDescription;
    public final String status;
    public final LocalDateTime createdAt;
    public final LocalDateTime updatedAt;
    public final Quote quote;               // null when no quote has been issued
    public final List<HistoryEntry> history;

    public static final class Quote {
        public final long id;
        public final double amount;
        public final String description;
        public final String status;         // PENDING, APPROVED or DECLINED
        public final String customerComment;
        public final LocalDateTime createdAt;
        public final LocalDateTime respondedAt;

        Quote(JSONObject json) throws JSONException {
            id = json.getLong("id");
            amount = json.getDouble("amount");
            description = json.getString("description");
            status = json.getString("status");
            customerComment = optString(json, "customerComment");
            createdAt = parseTime(optString(json, "createdAt"));
            respondedAt = parseTime(optString(json, "respondedAt"));
        }

        public boolean isPending() { return "PENDING".equals(status); }

        public String formattedAmount() { return formatMoney(amount); }
    }

    public static final class HistoryEntry {
        public final String from;
        public final String to;
        public final String note;
        public final LocalDateTime at;

        HistoryEntry(JSONObject json) throws JSONException {
            from = json.getString("from");
            to = json.getString("to");
            note = optString(json, "note");
            at = parseTime(optString(json, "at"));
        }

        /** "DIAGNOSING → REPAIRING · note" for a status change, or just the note for a quote event. */
        public String describe() {
            String change = from.equals(to) ? "" : from + " → " + to;
            if (note == null) return change;
            return change.isEmpty() ? note : change + " · " + note;
        }
    }

    private Ticket(JSONObject json) throws JSONException {
        id = json.getLong("id");
        version = json.optLong("version", 0);
        customerName = json.getString("customerName");
        customerEmail = json.getString("customerEmail");
        deviceType = json.getString("deviceType");
        deviceModel = optString(json, "deviceModel");
        issueDescription = json.getString("issueDescription");
        status = json.getString("status");
        createdAt = parseTime(optString(json, "createdAt"));
        updatedAt = parseTime(optString(json, "updatedAt"));
        quote = json.isNull("quote") ? null : new Quote(json.getJSONObject("quote"));

        List<HistoryEntry> entries = new ArrayList<>();
        JSONArray array = json.optJSONArray("history");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                entries.add(new HistoryEntry(array.getJSONObject(i)));
            }
        }
        history = Collections.unmodifiableList(entries);
    }

    public static Ticket fromJson(JSONObject json) throws JSONException {
        return new Ticket(json);
    }

    public static List<Ticket> listFromJson(JSONArray array) throws JSONException {
        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            tickets.add(new Ticket(array.getJSONObject(i)));
        }
        return tickets;
    }

    /** "iPhone 13" or "PHONE" when the model was not given. */
    public String deviceLabel() {
        return deviceModel == null || deviceModel.trim().isEmpty() ? deviceType : deviceModel;
    }

    public boolean hasPendingQuote() {
        return quote != null && quote.isPending();
    }

    public boolean canCancel() {
        return "RECEIVED".equals(status);
    }

    public static String formatTime(LocalDateTime time) {
        return time == null ? "" : time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT));
    }

    public static String formatMoney(double amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "NZ"));
        return format.format(amount);
    }

    private static String optString(JSONObject json, String key) {
        return json.isNull(key) ? null : json.optString(key, null);
    }

    private static LocalDateTime parseTime(String value) {
        if (value == null) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
