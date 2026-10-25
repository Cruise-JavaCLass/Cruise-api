package com.cruise.activity.dto;

import com.cruise.activity.enums.MusterStatus;
import com.cruise.activity.enums.SolasEmergencyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolasMusterIncidentDto {
    private Long id;

    @Size(max = 64, message = "incidentCode tối đa 64 ký tự")
    private String incidentCode;

    @NotNull(message = "Loại tình huống khẩn cấp là bắt buộc")
    private SolasEmergencyType emergencyType;

    private MusterStatus status;

    @Size(max = 100, message = "Người kích hoạt tối đa 100 ký tự")
    @NotBlank(message = "Người kích hoạt là bắt buộc")
    private String triggeredBy;

    @Size(max = 1000, message = "Nội dung thông báo khẩn cấp tối đa 1000 ký tự")
    @NotBlank(message = "Nội dung thông báo khẩn cấp là bắt buộc")
    private String announcementText;

    @Size(max = 50, message = "Mã tín hiệu âm thanh tối đa 50 ký tự")
    private String acousticSignalCode;

    private Integer totalSoulsOnBoard;
    private Integer musteredSoulsCount;
    private Double musterCompletionRate;
    private Instant triggeredAt;
    private Instant allClearAt;
    private Map<String, Long> headCountByStation;
}
