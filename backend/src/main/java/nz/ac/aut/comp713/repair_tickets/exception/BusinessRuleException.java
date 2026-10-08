package nz.ac.aut.comp713.repair_tickets.exception;

/** 請求格式正確，但違反商業規則時拋出（例如跳過狀態），API 層會轉成 HTTP 409。 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
