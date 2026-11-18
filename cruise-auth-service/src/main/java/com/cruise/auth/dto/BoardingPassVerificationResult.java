package com.cruise.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả xác thực thẻ lên tàu (Boarding Pass Verification Result - KAN-722).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardingPassVerificationResult {

    private boolean valid;
    private String message;
    private BoardingPassPayload payload;

    public static BoardingPassVerificationResult valid(BoardingPassPayload payload) {
        return BoardingPassVerificationResult.builder()
                .valid(true)
                .message("Xác thực thẻ lên tàu thành công")
                .payload(payload)
                .build();
    }

    public static BoardingPassVerificationResult invalid(String message) {
        return BoardingPassVerificationResult.builder()
                .valid(false)
                .message(message)
                .payload(null)
                .build();
    }
}
