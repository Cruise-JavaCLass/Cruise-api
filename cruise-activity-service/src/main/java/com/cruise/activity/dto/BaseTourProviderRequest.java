package com.cruise.activity.dto;

import com.cruise.activity.enums.TourProviderStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseTourProviderRequest {

    @Size(max = 40, message = "Mã nhà cung cấp không được vượt quá 40 ký tự")
    private String code;

    @NotBlank(message = "Tên nhà cung cấp không được để trống")
    @Size(max = 200, message = "Tên nhà cung cấp không được vượt quá 200 ký tự")
    private String name;

    @Size(max = 50, message = "Mã số thuế không được vượt quá 50 ký tự")
    private String taxCode;

    @Size(max = 100, message = "Số giấy phép không được vượt quá 100 ký tự")
    private String licenseNumber;

    @Size(max = 150, message = "Người liên hệ không được vượt quá 150 ký tự")
    private String contactPerson;

    @Email(message = "Email không hợp lệ")
    @Size(max = 254, message = "Email không được vượt quá 254 ký tự")
    private String email;

    @Size(max = 30, message = "Số điện thoại không được vượt quá 30 ký tự")
    private String phone;

    @NotBlank(message = "Hotline khẩn cấp không được để trống")
    @Pattern(regexp = "^\\+?[0-9][0-9\\s().-]{5,28}[0-9]$",
            message = "Hotline khẩn cấp không đúng định dạng")
    @Size(max = 30, message = "Hotline khẩn cấp không được vượt quá 30 ký tự")
    private String emergencyHotline;

    @NotBlank(message = "Mã cảng không được để trống")
    @Size(max = 100, message = "Mã cảng không được vượt quá 100 ký tự")
    private String portId;

    private TourProviderStatus status;
}
