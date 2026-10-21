package com.cruise.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BindRfidRequest {

    @NotBlank(message = "Mã thẻ RFID không được để trống")
    @Size(max = 50, message = "Mã RFID tối đa 50 ký tự")
    private String rfidTag;

    @Size(max = 100, message = "Mã QR tối đa 100 ký tự")
    private String qrCode;

    private Boolean activateFolio;
}
