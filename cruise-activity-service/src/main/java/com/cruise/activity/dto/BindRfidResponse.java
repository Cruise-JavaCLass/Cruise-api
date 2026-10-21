package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BindRfidResponse {

    private Long passengerId;
    private String rfidTag;
    private String qrCode;
    private String cabinNumber;
    private String folioStatus;
    private String message;
}
