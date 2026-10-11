package com.cruise.itinerary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PortRequest {

    @NotBlank(message = "Tên cảng không được để trống")
    @Size(max = 150, message = "Tên cảng không được vượt quá 150 ký tự")
    private String name;

    @NotBlank(message = "Mã cảng không được để trống")
    @Size(max = 50, message = "Mã cảng không được vượt quá 50 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9-_]+$", message = "Mã cảng chỉ chứa ký tự chữ, số, gạch nối hoặc gạch dưới")
    private String code;

    @Size(max = 100, message = "Quốc gia không được vượt quá 100 ký tự")
    private String country = "Việt Nam";

    @NotBlank(message = "Thành phố / Tỉnh không được để trống")
    @Size(max = 100, message = "Thành phố không được vượt quá 100 ký tự")
    private String city;

    private String timezone = "Asia/Ho_Chi_Minh";

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
    private String description;

    private Boolean active = true;
}
