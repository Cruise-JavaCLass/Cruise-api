package com.cruise.activity.dto;

import com.cruise.activity.entity.TourProvider;
import com.cruise.activity.enums.TourProviderStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class TourProviderResponse {

    private UUID id;
    private String code;
    private String name;
    private String taxCode;
    private String licenseNumber;
    private String contactPerson;
    private String email;
    private String phone;
    private String emergencyHotline;
    private String portId;
    private TourProviderStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static TourProviderResponse fromEntity(TourProvider provider) {
        return TourProviderResponse.builder()
                .id(provider.getId())
                .code(provider.getCode())
                .name(provider.getName())
                .taxCode(provider.getTaxCode())
                .licenseNumber(provider.getLicenseNumber())
                .contactPerson(provider.getContactPerson())
                .email(provider.getEmail())
                .phone(provider.getPhone())
                .emergencyHotline(provider.getEmergencyHotline())
                .portId(provider.getPortId())
                .status(provider.getStatus())
                .createdAt(provider.getCreatedAt())
                .updatedAt(provider.getUpdatedAt())
                .build();
    }
}
