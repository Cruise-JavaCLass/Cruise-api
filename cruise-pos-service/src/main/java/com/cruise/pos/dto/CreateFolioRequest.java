package com.cruise.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFolioRequest {

    @NotBlank(message = "Số phòng cabin không được để trống")
    private String cabinNumber;

    @NotBlank(message = "Tên khách chính không được để trống")
    private String primaryGuestName;

    private Long passengerId;

    @NotNull(message = "Hạn mức tín dụng không được để trống")
    @DecimalMin(value = "0.00", message = "Hạn mức tín dụng phải >= 0")
    private BigDecimal creditLimit;
}
