package com.cruise.activity.dto;

import com.cruise.activity.enums.GuideRole;
import com.cruise.activity.enums.GuideStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Base DTO chứa các trường thông tin chung của Hướng dẫn viên tour bờ (KAN-177)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseGuideRequest {

    private String fullName;
    private String phoneNumber;
    private GuideRole role;
    private List<String> languages;
    private String licenseNumber;
    private GuideStatus status;
    private Double rating;
    private String notes;
}
