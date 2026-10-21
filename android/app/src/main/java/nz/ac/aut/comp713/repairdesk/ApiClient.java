package nz.ac.aut.comp713.repairdesk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls the Repair Desk REST API. All methods block, so call them from a background thread.
 * Errors come back as ApiException carrying the server's ApiError fields.
 */
public final class ApiClient {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final String baseUrl;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** GET /api/tickets?email= — the signed-in customer's tickets, newest first. */
    public List<Ticket> listTickets(String email) throws ApiException {
        String body = send("GET", "/api/tickets?email=" + encode(email), null);
        try {
            return Ticket.listFromJson(new JSONArray(body));
        } catch (JSONException e) {
            throw badResponse(e);
        }
    }

    /** GET /api/tickets/{id} */
    public Ticket getTicket(long id) throws ApiException {
        return parseTicket(send("GET", "/api/tickets/" + id, null));
    }

    /** POST /api/tickets — book a new repair. Field errors come back in ApiException.getFieldErrors(). */
    public Ticket createTicket(String name, String email, String phone, String deviceType,
                               String deviceModel, String issueDescription) throws ApiException {
        JSONObject json = new JSONObject();
        try {
            json.put("name", name);
            json.put("email", email);
            json.put("phone", emptyToNull(phone));
            json.put("deviceType", deviceType);
            json.put("deviceModel", emptyToNull(deviceModel));
            json.put("issueDescription", issueDescription);
        } catch (JSONException e) {
            throw badResponse(e);
        }
        return parseTicket(send("POST", "/api/tickets", json));
    }

    /** POST /api/tickets/{id}/quote/decision — approve or decline the pending quote. */
    public Ticket decideQuote(long id, String email, boolean approved, String comment) throws ApiException {
        JSONObject json = new JSONObject();
        try {
            json.put("customerEmail", email);
            json.put("approved", approved);
            json.put("comment", emptyToNull(comment));
        } catch (JSONException e) {
            throw badResponse(e);
        }
        return parseTicket(send("POST", "/api/tickets/" + id + "/quote/decision", json));
    }

    /** DELETE /api/tickets/{id} — cancel a ticket that has not been started yet. */
    public void cancelTicket(long id) throws ApiException {
        send("DELETE", "/api/tickets/" + id, null);
    }

    private String send(String method, String path, JSONObject body) throws ApiException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = stream == null ? "" : readAll(stream);
            if (status >= 400) {
                throw toApiException(status, text);
            }
            return text;
        } catch (IOException e) {
            // Connection refused, timeout, no network, unknown host, malformed server address...
            throw ApiException.network(e.getClass().getSimpleName());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Turns the server's ApiError JSON into an exception; falls back to the HTTP status if the body is not JSON. */
    private static ApiException toApiException(int status, String text) {
        try {
            JSONObject json = new JSONObject(text);
            Map<String, String> fields = new LinkedHashMap<>();
            JSONObject fieldJson = json.optJSONObject("fieldErrors");
            if (fieldJson != null) {
                for (Iterator<String> keys = fieldJson.keys(); keys.hasNext(); ) {
                    String key = keys.next();
                    fields.put(key, fieldJson.optString(key));
                }
            }
            return new ApiException(status, json.optString("error", "HTTP_" + status),
                    json.optString("message", "Request failed"), fields);
        } catch (JSONException e) {
            return new ApiException(status, "HTTP_" + status, "Request failed", null);
        }
    }

    private static Ticket parseTicket(String body) throws ApiException {
        try {
            return Ticket.fromJson(new JSONObject(body));
        } catch (JSONException e) {
            throw badResponse(e);
        }
    }

    private static ApiException badResponse(JSONException e) {
        return new ApiException(500, "BAD_RESPONSE", "Unexpected response from server: " + e.getMessage(), null);
    }

    private static String readAll(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? JSONObject.NULL : value.trim();
    }
}
