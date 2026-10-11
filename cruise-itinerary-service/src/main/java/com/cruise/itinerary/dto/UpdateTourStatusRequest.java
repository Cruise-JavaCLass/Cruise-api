package com.cruise.itinerary.dto;

import com.cruise.itinerary.enums.TourStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO yêu cầu cập nhật trạng thái hoạt động của tour
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTourStatusRequest {

    @NotNull(message = "Trạng thái tour không được để trống")
    private TourStatus status;

    /**
     * Lý do thay đổi trạng thái (bắt buộc hoặc khuyến nghị khi hủy tour CANCELLED)
     */
    private String reason;

    public UpdateTourStatusRequest(TourStatus status) {
        this.status = status;
    }
}
