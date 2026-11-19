package com.cruise.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO phản hồi mã QR thẻ lên tàu kèm thông tin chi tiết dành cho hành khách (KAN-722).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassengerBoardingPassResponse {

    private String qrPayload;
    private BoardingPassPayload payload;
}
