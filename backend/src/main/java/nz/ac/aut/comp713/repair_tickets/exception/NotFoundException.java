package nz.ac.aut.comp713.repair_tickets.exception;

/** 找不到資料時拋出，API 層會轉成 HTTP 404。 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
