package nz.ac.aut.comp713.repair_tickets.model;

public enum TicketStatus {
    RECEIVED, DIAGNOSING, REPAIRING, READY, COLLECTED, CANCELLED;

    /** 只允許往下一個狀態走；RECEIVED 階段可以取消。 */
    public boolean canMoveTo(TicketStatus next) {
        return switch (this) {
            case RECEIVED   -> next == DIAGNOSING || next == CANCELLED;
            case DIAGNOSING -> next == REPAIRING;
            case REPAIRING  -> next == READY;
            case READY      -> next == COLLECTED;
            case COLLECTED, CANCELLED -> false;
        };
    }
}
