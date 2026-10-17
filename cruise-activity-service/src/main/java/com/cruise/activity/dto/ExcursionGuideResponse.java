package com.cruise.activity.dto;

import com.cruise.activity.entity.ExcursionGuide;
import com.cruise.activity.enums.GuideRole;
import com.cruise.activity.enums.GuideStatus;
import lombok.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Response DTO cho hướng dẫn viên tour bờ (KAN-177)
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionGuideResponse {

    private String id;
    private Long dbId;
    private Long excursionId;
    private String fullName;
    private String phoneNumber;
    private GuideRole role;
    private List<String> languages;
    private String licenseNumber;
    private GuideStatus status;
    private Double rating;
    private String notes;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public static ExcursionGuideResponse fromEntity(ExcursionGuide entity) {
        if (entity == null) {
            return null;
        }
        String formattedId = entity.getGuideCode() != null && !entity.getGuideCode().isBlank()
                ? entity.getGuideCode()
                : String.format("GUD-%02d", entity.getId() != null ? entity.getId() : 1);

        List<String> langList = Collections.emptyList();
        if (entity.getLanguages() != null && !entity.getLanguages().isBlank()) {
            langList = Arrays.stream(entity.getLanguages().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        return ExcursionGuideResponse.builder()
                .id(formattedId)
                .dbId(entity.getId())
                .excursionId(entity.getExcursionId())
                .fullName(entity.getFullName())
                .phoneNumber(entity.getPhoneNumber())
                .role(entity.getRole() != null ? entity.getRole() : GuideRole.LEAD)
                .languages(langList)
                .licenseNumber(entity.getLicenseNumber())
                .status(entity.getStatus() != null ? entity.getStatus() : GuideStatus.ACTIVE)
                .rating(entity.getRating() != null ? entity.getRating() : 5.0)
                .notes(entity.getNotes())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
