package com.cruise.activity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request gửi đánh giá phản hồi sau khi hoàn thành hoạt động (KAN-364)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitFeedbackRequest {

    @NotNull(message = "Passenger ID không được để trống")
    private Long passengerId;

    @NotNull(message = "Điểm đánh giá không được để trống")
    @Min(value = 1, message = "Điểm đánh giá tối thiểu là 1 sao")
    @Max(value = 5, message = "Điểm đánh giá tối đa là 5 sao")
    private Integer rating;

    @Min(value = 1, message = "Điểm tổ chức tối thiểu là 1 sao")
    @Max(value = 5, message = "Điểm tổ chức tối đa là 5 sao")
    private Integer organizationRating;

    @Min(value = 1, message = "Điểm hướng dẫn viên tối thiểu là 1 sao")
    @Max(value = 5, message = "Điểm hướng dẫn viên tối đa là 5 sao")
    private Integer guideRating;

    @Min(value = 1, message = "Điểm độ hài lòng chi phí tối thiểu là 1 sao")
    @Max(value = 5, message = "Điểm độ hài lòng chi phí tối đa là 5 sao")
    private Integer valueRating;

    @Size(max = 1000, message = "Nội dung nhận xét tối đa 1000 ký tự")
    private String comments;

    @Size(max = 100, message = "Idempotency key tối đa 100 ký tự")
    private String idempotencyKey;
}
