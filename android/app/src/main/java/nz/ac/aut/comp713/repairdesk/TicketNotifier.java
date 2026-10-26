package nz.ac.aut.comp713.repairdesk;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Mobile capability: posts a notification when a technician changes one of the customer's tickets
 * (a new quote to approve, or a status change such as READY for pickup).
 *
 * It compares the latest ticket list with the snapshot saved after the previous check. The first check
 * after sign-in only saves a snapshot, so old tickets do not produce a burst of notifications.
 */
public final class TicketNotifier {

    public static final String CHANNEL_ID = "ticket_updates";

    private TicketNotifier() { }

    public static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.channel_description));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    public static boolean canNotify(Context context) {
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                && context.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    /** Compare with the previous snapshot, notify about technician changes, then save the new snapshot. */
    public static synchronized void checkForChanges(Context context, List<Ticket> tickets) {
        JSONObject previous = loadSnapshot(context);
        JSONObject next = new JSONObject();
        for (Ticket ticket : tickets) {
            String key = String.valueOf(ticket.id);
            putQuietly(next, key, fingerprint(ticket));
            if (previous != null) {
                String message = describeChange(context, previous.optString(key, null), ticket);
                if (message != null) {
                    postNotification(context, ticket, message);
                }
            }
        }
        Prefs.saveSnapshot(context, next.toString());
    }

    /**
     * Record a change the customer made themselves (approve, decline, cancel, new ticket)
     * so the next check does not notify them about their own action.
     */
    public static synchronized void remember(Context context, Ticket ticket) {
        JSONObject snapshot = loadSnapshot(context);
        if (snapshot == null) return;   // no baseline yet; the next check creates one
        putQuietly(snapshot, String.valueOf(ticket.id), fingerprint(ticket));
        Prefs.saveSnapshot(context, snapshot.toString());
    }

    /** e.g. "DIAGNOSING|7|PENDING": status plus the current quote's id and state. */
    private static String fingerprint(Ticket ticket) {
        return ticket.status + "|" + (ticket.quote == null ? "-|-" : ticket.quote.id + "|" + ticket.quote.status);
    }

    /** Returns the notification text, or null when nothing the customer needs to know about changed. */
    private static String describeChange(Context context, String before, Ticket ticket) {
        String now = fingerprint(ticket);
        if (now.equals(before)) return null;
        if (before == null) return null;   // a ticket booked on the web; nothing to tell the customer yet

        String[] old = before.split("\\|");
        String device = ticket.deviceLabel();
        boolean newQuote = ticket.hasPendingQuote() && !String.valueOf(ticket.quote.id).equals(old[1]);
        if (newQuote) {
            return context.getString(R.string.notify_quote, device, ticket.quote.formattedAmount());
        }
        if (!ticket.status.equals(old[0])) {
            if ("READY".equals(ticket.status)) {
                return context.getString(R.string.notify_ready, device);
            }
            return context.getString(R.string.notify_status, device, ticket.status);
        }
        return null;
    }

    private static void postNotification(Context context, Ticket ticket, String message) {
        if (!canNotify(context)) return;   // permission denied: updates are still shown when the app is opened

        Intent open = new Intent(context, TicketDetailActivity.class)
                .putExtra(TicketDetailActivity.EXTRA_TICKET_ID, ticket.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, (int) ticket.id, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notify_title, ticket.id))
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build();
        // One notification per ticket: a newer update replaces the older one
        context.getSystemService(NotificationManager.class).notify((int) ticket.id, notification);
    }

    private static JSONObject loadSnapshot(Context context) {
        String json = Prefs.snapshot(context);
        if (json == null) return null;
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            return null;
        }
    }

    private static void putQuietly(JSONObject json, String key, String value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
