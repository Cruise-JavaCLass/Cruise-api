package com.cruise.pos.exception;

/**
 * Ném khi một khoản chi tiêu làm vượt hạn mức tín dụng cabin (cộng các khoản tạm giữ đang hiệu lực)
 */
public class FolioLimitExceededException extends BusinessRuleException {

    public static final String ERROR_CODE = "FOLIO_LIMIT_EXCEEDED";

    public FolioLimitExceededException(String detail) {
        super(ERROR_CODE + ": " + detail);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
