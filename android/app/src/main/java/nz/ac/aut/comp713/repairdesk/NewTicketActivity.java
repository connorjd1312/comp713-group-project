package nz.ac.aut.comp713.repairdesk;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Map;

/**
 * Screen 2: book a repair (POST /api/tickets) for the signed-in customer.
 * Validation is done by the server; its field errors are shown on the matching inputs.
 */
public class NewTicketActivity extends Activity {

    private EditText nameInput;
    private EditText phoneInput;
    private Spinner deviceTypeSpinner;
    private EditText modelInput;
    private EditText issueInput;
    private TextView formMessage;
    private Button submitButton;
    private ProgressBar submitting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_ticket);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        nameInput = findViewById(R.id.nameInput);
        phoneInput = findViewById(R.id.phoneInput);
        deviceTypeSpinner = findViewById(R.id.deviceTypeSpinner);
        modelInput = findViewById(R.id.modelInput);
        issueInput = findViewById(R.id.issueInput);
        formMessage = findViewById(R.id.formMessage);
        submitButton = findViewById(R.id.submitButton);
        submitting = findViewById(R.id.submitting);

        ((TextView) findViewById(R.id.bookingFor)).setText(getString(R.string.booking_for, Prefs.email(this)));
        submitButton.setOnClickListener(v -> submit());
    }

    private void submit() {
        clearErrors();
        String email = Prefs.email(this);
        if (email == null) {   // signed out in the meantime
            finish();
            return;
        }
        setBusy(true);
        ApiClient api = Prefs.api(this);
        String name = nameInput.getText().toString();
        String phone = phoneInput.getText().toString();
        String deviceType = deviceTypeSpinner.getSelectedItem().toString();
        String model = modelInput.getText().toString();
        String issue = issueInput.getText().toString();

        RepairDeskApp.io().execute(() -> {
            try {
                Ticket created = api.createTicket(name, email, phone, deviceType, model, issue);
                TicketNotifier.remember(getApplicationContext(), created);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    startActivity(new Intent(this, TicketDetailActivity.class)
                            .putExtra(TicketDetailActivity.EXTRA_TICKET_ID, created.id)
                            .putExtra(TicketDetailActivity.EXTRA_JUST_CREATED, true));
                    finish();
                });
            } catch (ApiException e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    setBusy(false);
                    showErrors(e);
                });
            }
        });
    }

    private void showErrors(ApiException e) {
        Map<String, String> fields = e.getFieldErrors();
        setFieldError(nameInput, fields.get("name"));
        setFieldError(phoneInput, fields.get("phone"));
        setFieldError(modelInput, fields.get("deviceModel"));
        setFieldError(issueInput, fields.get("issueDescription"));
        // email and deviceType come from the app itself, so show them in the message if the server rejects them
        StringBuilder message = new StringBuilder(e.describe());
        for (String key : new String[]{"email", "deviceType"}) {
            if (fields.containsKey(key)) message.append("\n• ").append(fields.get(key));
        }
        formMessage.setText(message);
        formMessage.setVisibility(View.VISIBLE);
    }

    private static void setFieldError(EditText input, String error) {
        if (error != null) input.setError(error);
    }

    private void clearErrors() {
        for (EditText input : new EditText[]{nameInput, phoneInput, modelInput, issueInput}) {
            input.setError(null);
        }
        formMessage.setVisibility(View.GONE);
    }

    /** Disables the button while sending so the ticket cannot be submitted twice. */
    private void setBusy(boolean busy) {
        submitButton.setEnabled(!busy);
        submitting.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
