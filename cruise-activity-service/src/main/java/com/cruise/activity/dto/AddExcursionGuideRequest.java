package com.cruise.activity.dto;

import com.cruise.activity.enums.GuideRole;
import com.cruise.activity.enums.GuideStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Request DTO thêm hướng dẫn viên vào tour bờ (KAN-177)
 */
@Getter
@Setter
@NoArgsConstructor
public class AddExcursionGuideRequest extends BaseGuideRequest {

    @NotBlank(message = "Họ tên hướng dẫn viên không được để trống")
    @Override
    public String getFullName() {
        return super.getFullName();
    }

    @Builder
    public AddExcursionGuideRequest(String fullName, String phoneNumber, GuideRole role,
                                   List<String> languages, String licenseNumber,
                                   GuideStatus status, Double rating, String notes) {
        super(fullName, phoneNumber, role, languages, licenseNumber, status, rating, notes);
    }
}
