package nz.ac.aut.comp713.repairdesk;

import android.content.Context;
import android.content.SharedPreferences;

/** The signed-in customer's email, the server address and the last ticket snapshot used for notifications. */
public final class Prefs {

    private static final String FILE = "repair_desk";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_SERVER = "server_url";
    private static final String KEY_SNAPSHOT = "ticket_snapshot";

    private Prefs() { }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String email(Context context) {
        return prefs(context).getString(KEY_EMAIL, null);
    }

    public static boolean isSignedIn(Context context) {
        return email(context) != null;
    }

    public static String serverUrl(Context context) {
        return prefs(context).getString(KEY_SERVER, BuildConfig.DEFAULT_SERVER_URL);
    }

    public static void signIn(Context context, String email, String serverUrl) {
        prefs(context).edit()
                .putString(KEY_EMAIL, email.trim().toLowerCase())
                .putString(KEY_SERVER, serverUrl.trim())
                .remove(KEY_SNAPSHOT)
                .apply();
    }

    public static void signOut(Context context) {
        prefs(context).edit().remove(KEY_EMAIL).remove(KEY_SNAPSHOT).apply();
    }

    public static ApiClient api(Context context) {
        return new ApiClient(serverUrl(context));
    }

    static String snapshot(Context context) {
        return prefs(context).getString(KEY_SNAPSHOT, null);
    }

    static void saveSnapshot(Context context, String json) {
        prefs(context).edit().putString(KEY_SNAPSHOT, json).apply();
    }
}
