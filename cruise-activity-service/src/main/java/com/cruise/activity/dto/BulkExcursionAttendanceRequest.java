package com.cruise.activity.dto;

import com.cruise.activity.enums.ExcursionPassengerStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request điểm danh hàng loạt theo danh sách ID, đoàn hoặc xe (KAN-316)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkExcursionAttendanceRequest {

    /**
     * Danh sách ID hành khách tour bờ cần cập nhật (tùy chọn nếu lọc theo group/bus)
     */
    private List<Long> passengerIds;

    /**
     * Lọc theo tên đoàn / nhóm (nếu điểm danh toàn bộ đoàn)
     */
    private String groupName;

    /**
     * Lọc theo số xe buýt (nếu điểm danh toàn bộ xe)
     */
    private String busNumber;

    /**
     * Trạng thái điểm danh đích: CHECKED_IN, ON_TOUR, hoặc RETURNED_SAFE
     */
    @NotNull(message = "Trạng thái điểm danh không được để trống")
    private ExcursionPassengerStatus targetStatus;

    /**
     * Ghi chú bổ sung
     */
    private String note;
}
