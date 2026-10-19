package nz.ac.aut.comp713.repair_tickets.exception;

/** Thrown when a user acts on data that belongs to someone else; the API layer maps it to HTTP 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
