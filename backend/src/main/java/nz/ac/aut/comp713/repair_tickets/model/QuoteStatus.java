package nz.ac.aut.comp713.repair_tickets.model;

/** A quote waits for the customer's decision; only an approved quote lets repair work start. */
public enum QuoteStatus {
    PENDING, APPROVED, DECLINED
}
