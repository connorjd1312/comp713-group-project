package nz.ac.aut.comp713.repairdesk;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen 1: customer sign-in (by email) and the list of their repairs.
 * Handles loading, empty and error (with retry) states, and asks for the notification permission.
 */
public class MainActivity extends Activity {

    private static final int REQUEST_NOTIFICATIONS = 1;

    private View signInPanel;
    private View ticketsPanel;
    private EditText emailInput;
    private EditText serverInput;
    private TextView signedInAs;
    private TextView notificationBanner;
    private ListView ticketList;
    private ProgressBar loading;
    private View statePanel;
    private TextView stateText;
    private Button retryButton;

    private final TicketAdapter adapter = new TicketAdapter();
    private int loadGeneration;   // ignores results of an older request that finishes after a newer one

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        signInPanel = findViewById(R.id.signInPanel);
        ticketsPanel = findViewById(R.id.ticketsPanel);
        emailInput = findViewById(R.id.emailInput);
        serverInput = findViewById(R.id.serverInput);
        signedInAs = findViewById(R.id.signedInAs);
        notificationBanner = findViewById(R.id.notificationBanner);
        ticketList = findViewById(R.id.ticketList);
        loading = findViewById(R.id.loading);
        statePanel = findViewById(R.id.statePanel);
        stateText = findViewById(R.id.stateText);
        retryButton = findViewById(R.id.retryButton);

        ticketList.setAdapter(adapter);
        ticketList.setOnItemClickListener((parent, view, position, id) ->
                startActivity(new Intent(this, TicketDetailActivity.class)
                        .putExtra(TicketDetailActivity.EXTRA_TICKET_ID, adapter.getItem(position).id)));

        findViewById(R.id.signInButton).setOnClickListener(v -> signIn());
        findViewById(R.id.newTicketButton).setOnClickListener(v ->
                startActivity(new Intent(this, NewTicketActivity.class)));
        findViewById(R.id.refreshButton).setOnClickListener(v -> loadTickets());
        findViewById(R.id.signOutButton).setOnClickListener(v -> signOut());
        retryButton.setOnClickListener(v -> loadTickets());
        notificationBanner.setOnClickListener(v -> openNotificationSettings());

        if (savedInstanceState == null) {
            serverInput.setText(Prefs.serverUrl(this));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh every time the screen is shown, e.g. after booking a repair or approving a quote
        if (Prefs.isSignedIn(this)) {
            showTickets();
        } else {
            signInPanel.setVisibility(View.VISIBLE);
            ticketsPanel.setVisibility(View.GONE);
        }
    }

    private void signIn() {
        String email = emailInput.getText().toString().trim();
        String server = serverInput.getText().toString().trim();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError(getString(R.string.error_email));
            return;
        }
        if (!Patterns.WEB_URL.matcher(server).matches() || !server.startsWith("http")) {
            serverInput.setError(getString(R.string.error_server));
            return;
        }
        Prefs.signIn(this, email, server);
        RepairDeskApp.scheduleBackgroundJob(this);
        requestNotificationPermission();
        showTickets();
    }

    private void signOut() {
        Prefs.signOut(this);
        RepairDeskApp.cancelBackgroundJob(this);
        adapter.setTickets(new ArrayList<>());
        emailInput.setText("");
        signInPanel.setVisibility(View.VISIBLE);
        ticketsPanel.setVisibility(View.GONE);
    }

    private void showTickets() {
        signInPanel.setVisibility(View.GONE);
        ticketsPanel.setVisibility(View.VISIBLE);
        signedInAs.setText(getString(R.string.signed_in_as, Prefs.email(this), Prefs.serverUrl(this)));
        notificationBanner.setVisibility(TicketNotifier.canNotify(this) ? View.GONE : View.VISIBLE);
        loadTickets();
    }

    private void loadTickets() {
        int generation = ++loadGeneration;
        String email = Prefs.email(this);
        if (email == null) return;

        loading.setVisibility(View.VISIBLE);
        statePanel.setVisibility(View.GONE);
        ApiClient api = Prefs.api(this);
        RepairDeskApp.io().execute(() -> {
            try {
                List<Ticket> tickets = api.listTickets(email);
                TicketNotifier.checkForChanges(getApplicationContext(), tickets);
                runOnUiThread(() -> { if (isCurrent(generation)) showList(tickets); });
            } catch (ApiException e) {
                runOnUiThread(() -> { if (isCurrent(generation)) showError(e); });
            }
        });
    }

    private boolean isCurrent(int generation) {
        return !isDestroyed() && generation == loadGeneration;
    }

    private void showList(List<Ticket> tickets) {
        loading.setVisibility(View.GONE);
        adapter.setTickets(tickets);
        if (tickets.isEmpty()) {
            stateText.setText(R.string.empty_tickets);
            retryButton.setVisibility(View.GONE);
            statePanel.setVisibility(View.VISIBLE);
        }
    }

    /** Keeps the last successful list visible (if any) and explains what went wrong. */
    private void showError(ApiException e) {
        loading.setVisibility(View.GONE);
        stateText.setText(getString(R.string.error_load_tickets, e.describe()));
        retryButton.setVisibility(View.VISIBLE);
        statePanel.setVisibility(View.VISIBLE);
    }

    private void requestNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            // Denied: the app still works; the banner explains that updates only appear when the app is opened
            notificationBanner.setVisibility(TicketNotifier.canNotify(this) ? View.GONE : View.VISIBLE);
        }
    }

    private void openNotificationSettings() {
        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }

    /** Ticket rows: device, status badge, date, and a highlight when a quote needs an answer. */
    private final class TicketAdapter extends BaseAdapter {
        private List<Ticket> tickets = new ArrayList<>();

        void setTickets(List<Ticket> tickets) {
            this.tickets = tickets;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return tickets.size(); }
        @Override public Ticket getItem(int position) { return tickets.get(position); }
        @Override public long getItemId(int position) { return tickets.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ticket, parent, false);
            Ticket t = getItem(position);
            ((TextView) row.findViewById(R.id.ticketTitle)).setText(getString(R.string.ticket_title, t.id, t.deviceLabel()));
            StatusStyle.apply(row.findViewById(R.id.ticketStatus), t.status);
            ((TextView) row.findViewById(R.id.ticketSubtitle)).setText(
                    getString(R.string.ticket_updated, Ticket.formatTime(t.updatedAt)));
            row.findViewById(R.id.ticketAction).setVisibility(t.hasPendingQuote() ? View.VISIBLE : View.GONE);
            return row;
        }
    }
}
