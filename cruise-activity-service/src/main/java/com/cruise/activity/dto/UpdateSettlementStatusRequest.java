package com.cruise.activity.dto;

import com.cruise.activity.enums.SettlementStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO cập nhật trạng thái quyết toán (KAN-332, KAN-334)
 */
public record UpdateSettlementStatusRequest(
        @NotNull(message = "Trạng thái mới không được để trống")
        SettlementStatus targetStatus,

        @Size(max = 100, message = "Người thực hiện tối đa 100 ký tự")
        String actor,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note
) {}
