package com.cruise.itinerary.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipDeckZoneDto {
    private Long id;

    @NotNull(message = "shipId là bắt buộc")
    private Long shipId;

    @NotNull(message = "deckNumber là bắt buộc")
    @Min(value = 1, message = "Tầng phải từ 1 trở lên")
    private Integer deckNumber;

    @Size(max = 100, message = "zoneName tối đa 100 ký tự")
    @NotBlank(message = "zoneName không được để trống")
    private String zoneName;

    @Size(max = 50, message = "zoneCode tối đa 50 ký tự")
    @NotBlank(message = "zoneCode không được để trống")
    private String zoneCode;

    @NotNull(message = "maxSafetyCapacity là bắt buộc")
    @Min(value = 1, message = "Sức chứa an toàn tối thiểu 1 người")
    private Integer maxSafetyCapacity;

    @Size(max = 50, message = "zoneType tối đa 50 ký tự")
    @NotBlank(message = "zoneType không được để trống")
    private String zoneType;

    private Boolean hasLifeboatAccess;
    private Boolean isRestricted;
}
