package nz.ac.aut.comp713.repairdesk;

import java.util.Collections;
import java.util.Map;

/**
 * A failed API call. Mirrors the server's ApiError JSON; status 0 means the server could not be reached.
 */
public class ApiException extends Exception {

    public static final int NETWORK = 0;

    private final int status;
    private final String code;
    private final Map<String, String> fieldErrors;

    public ApiException(int status, String code, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors == null ? Collections.emptyMap() : fieldErrors;
    }

    public static ApiException network(String detail) {
        return new ApiException(NETWORK, "NETWORK_ERROR",
                "Cannot reach the repair server. Check your connection and try again. (" + detail + ")", null);
    }

    public boolean isNetworkError() { return status == NETWORK; }
    public int getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }

    /** One line for the UI, e.g. "Ticket 2 has no quote waiting for a decision (HTTP 409)". */
    public String describe() {
        return isNetworkError() ? getMessage() : getMessage() + " (HTTP " + status + ")";
    }
}
