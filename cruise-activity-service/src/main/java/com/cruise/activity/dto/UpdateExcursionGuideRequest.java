package com.cruise.activity.dto;

import com.cruise.activity.enums.GuideRole;
import com.cruise.activity.enums.GuideStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Request DTO cập nhật hướng dẫn viên tour bờ (KAN-177)
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateExcursionGuideRequest extends BaseGuideRequest {

    @Builder
    public UpdateExcursionGuideRequest(String fullName, String phoneNumber, GuideRole role,
                                      List<String> languages, String licenseNumber,
                                      GuideStatus status, Double rating, String notes) {
        super(fullName, phoneNumber, role, languages, licenseNumber, status, rating, notes);
    }
}
