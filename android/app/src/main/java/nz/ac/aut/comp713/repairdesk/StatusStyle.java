package nz.ac.aut.comp713.repairdesk;

import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

/** Status badge colours, matching the web client's style.css. */
final class StatusStyle {

    private StatusStyle() { }

    static void apply(TextView badge, String status) {
        int background;
        int text;
        switch (status) {
            case "RECEIVED":   background = 0xFFE3F2FD; text = 0xFF0D47A1; break;
            case "DIAGNOSING": background = 0xFFFFF3E0; text = 0xFFB45309; break;
            case "REPAIRING":  background = 0xFFEDE7F6; text = 0xFF4527A0; break;
            case "READY":
            case "APPROVED":   background = 0xFFE8F5E9; text = 0xFF1B5E20; break;
            case "PENDING":    background = 0xFFFFF8E1; text = 0xFF92400E; break;
            case "CANCELLED":
            case "DECLINED":   background = 0xFFFDECEA; text = 0xFF8E1C1C; break;
            default:           background = 0xFFECEFF1; text = 0xFF37474F; break;
        }
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(999f);
        shape.setColor(background);
        badge.setBackground(shape);
        badge.setTextColor(text);
        badge.setText(status);
    }
}
