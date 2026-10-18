package com.cruise.activity.dto;

import com.cruise.activity.enums.ExcursionPassengerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response kết quả điểm danh hàng loạt (KAN-316)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkExcursionAttendanceResponse {

    private Long excursionId;
    private int totalRequested;
    private int totalUpdated;
    private ExcursionPassengerStatus targetStatus;
    private List<Long> updatedPassengerIds;
    private List<Long> skippedPassengerIds;
    private String message;
}
