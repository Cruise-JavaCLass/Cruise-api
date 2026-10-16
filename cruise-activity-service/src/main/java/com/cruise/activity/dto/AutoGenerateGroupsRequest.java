package com.cruise.activity.dto;

import jakarta.validation.constraints.Min;
import lombok.*;

/**
 * Request DTO cho việc tự động sinh/phân nhóm hành khách tour bờ (KAN-178)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoGenerateGroupsRequest {

    @Min(value = 1, message = "Số nhóm mục tiêu tối thiểu phải là 1")
    private Integer targetGroupCount;

    @Min(value = 1, message = "Sức chứa mỗi nhóm tối thiểu phải là 1")
    private Integer maxPassengersPerGroup;

    private String groupPrefix;

    @Builder.Default
    private Boolean autoAssignVehicles = true;
}
