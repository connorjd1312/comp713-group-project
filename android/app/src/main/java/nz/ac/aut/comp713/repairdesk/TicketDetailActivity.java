package nz.ac.aut.comp713.repairdesk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;

/**
 * Screen 3: one repair's progress and history, and the customer's part of the quote workflow:
 * approve or decline the technician's quote (POST /api/tickets/{id}/quote/decision).
 * Also opened from a notification.
 */
public class TicketDetailActivity extends Activity {

    public static final String EXTRA_TICKET_ID = "ticket_id";
    public static final String EXTRA_JUST_CREATED = "just_created";

    private long ticketId;
    private Ticket ticket;
    private int loadGeneration;

    private View content;
    private ProgressBar loading;
    private View errorPanel;
    private TextView errorText;
    private TextView message;
    private EditText commentInput;
    private Button approveButton;
    private Button declineButton;
    private Button cancelButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ticket_detail);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        content = findViewById(R.id.content);
        loading = findViewById(R.id.loading);
        errorPanel = findViewById(R.id.errorPanel);
        errorText = findViewById(R.id.errorText);
        message = findViewById(R.id.message);
        commentInput = findViewById(R.id.commentInput);
        approveButton = findViewById(R.id.approveButton);
        declineButton = findViewById(R.id.declineButton);
        cancelButton = findViewById(R.id.cancelButton);

        approveButton.setOnClickListener(v -> decide(true));
        declineButton.setOnClickListener(v -> confirmDecline());
        cancelButton.setOnClickListener(v -> confirmCancel());
        findViewById(R.id.retryButton).setOnClickListener(v -> load());

        readIntent(getIntent());
        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_JUST_CREATED, false)) {
            showMessage(true, getString(R.string.ticket_created));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);   // tapped a notification while this screen was open
        setIntent(intent);
        readIntent(intent);
    }

    private void readIntent(Intent intent) {
        long id = intent.getLongExtra(EXTRA_TICKET_ID, -1);
        if (id != ticketId) ticket = null;   // a notification opened a different ticket
        ticketId = id;
        if (getActionBar() != null) getActionBar().setTitle(getString(R.string.title_ticket_number, ticketId));
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();   // also picks up changes made on the web while this screen was in the background
    }

    private void load() {
        int generation = ++loadGeneration;
        if (!Prefs.isSignedIn(this)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        if (ticket == null) {
            loading.setVisibility(View.VISIBLE);
            errorPanel.setVisibility(View.GONE);
        }
        long id = ticketId;
        ApiClient api = Prefs.api(this);
        RepairDeskApp.io().execute(() -> {
            try {
                Ticket loaded = api.getTicket(id);
                TicketNotifier.remember(getApplicationContext(), loaded);   // the customer has now seen it
                runOnUiThread(() -> {
                    if (!isDestroyed() && id == ticketId && generation == loadGeneration) render(loaded);
                });
            } catch (ApiException e) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && id == ticketId && generation == loadGeneration) showLoadError(e);
                });
            }
        });
    }

    private void render(Ticket t) {
        if (t.id != ticketId || (ticket != null && t.version < ticket.version)) return;
        ticket = t;
        loading.setVisibility(View.GONE);
        errorPanel.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);

        ((TextView) findViewById(R.id.title)).setText(t.deviceLabel());
        StatusStyle.apply(findViewById(R.id.status), t.status);
        ((TextView) findViewById(R.id.progress)).setText(progressLine(t.status));
        ((TextView) findViewById(R.id.issue)).setText(t.issueDescription);
        ((TextView) findViewById(R.id.dates)).setText(getString(R.string.ticket_dates,
                Ticket.formatTime(t.createdAt), Ticket.formatTime(t.updatedAt)));

        renderQuote(t);
        renderHistory(t.history);
        cancelButton.setVisibility(t.canCancel() ? View.VISIBLE : View.GONE);
        invalidateOptionsMenu();
    }

    /** e.g. "✓ RECEIVED  ● DIAGNOSING  ○ REPAIRING  ○ READY  ○ COLLECTED" */
    private static String progressLine(String status) {
        if ("CANCELLED".equals(status)) return "✕ CANCELLED";
        int position = Ticket.STATUS_FLOW.indexOf(status);
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < Ticket.STATUS_FLOW.size(); i++) {
            line.append(i < position ? "✓ " : i == position ? "● " : "○ ")
                .append(Ticket.STATUS_FLOW.get(i)).append("  ");
        }
        return line.toString().trim();
    }

    private void renderQuote(Ticket t) {
        View card = findViewById(R.id.quoteCard);
        Ticket.Quote q = t.quote;
        if (q == null) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);
        StatusStyle.apply(findViewById(R.id.quoteStatus), q.status);
        ((TextView) findViewById(R.id.quoteAmount)).setText(q.formattedAmount());
        ((TextView) findViewById(R.id.quoteDescription)).setText(q.description);

        String meta = getString(R.string.quote_sent, Ticket.formatTime(q.createdAt));
        if (q.respondedAt != null) {
            meta += " · " + getString(R.string.quote_answered, Ticket.formatTime(q.respondedAt));
        }
        if (q.customerComment != null) {
            meta += "\n“" + q.customerComment + "”";
        }
        ((TextView) findViewById(R.id.quoteMeta)).setText(meta);
        findViewById(R.id.decisionPanel).setVisibility(q.isPending() ? View.VISIBLE : View.GONE);
    }

    private void renderHistory(List<Ticket.HistoryEntry> history) {
        LinearLayout list = findViewById(R.id.history);
        list.removeAllViews();
        if (history.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(R.string.history_empty);
            none.setTextColor(getColor(R.color.muted));
            list.addView(none);
            return;
        }
        // newest first, like the web client
        for (int i = history.size() - 1; i >= 0; i--) {
            Ticket.HistoryEntry entry = history.get(i);
            TextView row = new TextView(this);
            row.setText(entry.describe() + "\n" + Ticket.formatTime(entry.at));
            row.setPadding(0, 12, 0, 12);
            list.addView(row);
        }
    }

    private void confirmDecline() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.decline_title)
                .setMessage(R.string.decline_message)
                .setPositiveButton(R.string.action_decline, (d, w) -> decide(false))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void decide(boolean approved) {
        String email = Prefs.email(this);
        if (ticket == null || email == null) return;
        setActionsEnabled(false);
        hideMessage();
        ApiClient api = Prefs.api(this);
        long id = ticket.id;
        String comment = commentInput.getText().toString();
        RepairDeskApp.io().execute(() -> {
            try {
                Ticket updated = api.decideQuote(id, email, approved, comment);
                TicketNotifier.remember(getApplicationContext(), updated);
                runOnUiThread(() -> {
                    if (isDestroyed() || id != ticketId) return;
                    setActionsEnabled(true);
                    commentInput.setText("");
                    render(updated);
                    showMessage(true, getString(approved ? R.string.quote_approved_message : R.string.quote_declined_message));
                });
            } catch (ApiException e) {
                runOnUiThread(() -> {
                    if (isDestroyed() || id != ticketId) return;
                    setActionsEnabled(true);
                    showMessage(false, e.describe());
                    if (e.getStatus() == 409) load();   // the quote changed on the server; show the current state
                });
            }
        });
    }

    private void confirmCancel() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cancel_title)
                .setMessage(R.string.cancel_message)
                .setPositiveButton(R.string.action_cancel_ticket, (d, w) -> cancelTicket())
                .setNegativeButton(R.string.action_keep, null)
                .show();
    }

    private void cancelTicket() {
        if (ticket == null) return;
        setActionsEnabled(false);
        hideMessage();
        ApiClient api = Prefs.api(this);
        long id = ticket.id;
        RepairDeskApp.io().execute(() -> {
            try {
                api.cancelTicket(id);
                Ticket updated = api.getTicket(id);
                TicketNotifier.remember(getApplicationContext(), updated);
                runOnUiThread(() -> {
                    if (isDestroyed() || id != ticketId) return;
                    setActionsEnabled(true);
                    render(updated);
                    showMessage(true, getString(R.string.ticket_cancelled));
                });
            } catch (ApiException e) {
                runOnUiThread(() -> {
                    if (isDestroyed() || id != ticketId) return;
                    setActionsEnabled(true);
                    showMessage(false, e.describe());
                    if (e.getStatus() == 409) load();   // e.g. the technician already started diagnosis
                });
            }
        });
    }

    /** Prevents a second tap while a request is in flight. */
    private void setActionsEnabled(boolean enabled) {
        approveButton.setEnabled(enabled);
        declineButton.setEnabled(enabled);
        cancelButton.setEnabled(enabled);
    }

    private void showLoadError(ApiException e) {
        loading.setVisibility(View.GONE);
        if (ticket != null) {
            showMessage(false, getString(R.string.error_refresh, e.describe()));   // keep showing the last data
            return;
        }
        content.setVisibility(View.GONE);
        errorText.setText(e.getStatus() == 404 ? getString(R.string.error_not_found)
                : getString(R.string.error_load_ticket, e.describe()));
        errorPanel.setVisibility(View.VISIBLE);
    }

    private void showMessage(boolean success, String text) {
        message.setBackgroundResource(success ? R.drawable.banner_success : R.drawable.banner_error);
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }

    private void hideMessage() {
        message.setVisibility(View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, R.id.menu_refresh, Menu.NONE, R.string.action_refresh)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Opened from a notification there may be no MainActivity underneath
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
            return true;
        }
        if (item.getItemId() == R.id.menu_refresh) {
            hideMessage();
            load();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
