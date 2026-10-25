package com.cruise.activity.dto;

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
public class SolasMusterCheckinRequest {
    @NotNull(message = "incidentId là bắt buộc")
    private Long incidentId;

    @NotNull(message = "passengerId là bắt buộc")
    private Long passengerId;

    @Size(max = 32, message = "Trạm tập trung musterStationCode tối đa 32 ký tự")
    @NotBlank(message = "Trạm tập trung musterStationCode là bắt buộc")
    private String musterStationCode;

    private Boolean lifejacketVerified;

    @Size(max = 100, message = "checkedInBy tối đa 100 ký tự")
    private String checkedInBy;
}
