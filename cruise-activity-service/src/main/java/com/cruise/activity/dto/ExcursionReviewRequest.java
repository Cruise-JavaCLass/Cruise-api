package com.cruise.activity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExcursionReviewRequest(
        @NotNull(message = "Passenger ID không được để trống")
        Long passengerId,

        @NotNull(message = "Điểm đánh giá tổng thể không được để trống")
        @Min(value = 1, message = "Điểm đánh giá tối thiểu là 1 sao")
        @Max(value = 5, message = "Điểm đánh giá tối đa là 5 sao")
        Integer ratingOverall,

        @Min(value = 1, message = "Điểm hướng dẫn viên tối thiểu là 1 sao")
        @Max(value = 5, message = "Điểm hướng dẫn viên tối đa là 5 sao")
        Integer ratingGuide,

        @Min(value = 1, message = "Điểm xe đưa đón tối thiểu là 1 sao")
        @Max(value = 5, message = "Điểm xe đưa đón tối đa là 5 sao")
        Integer ratingBus,

        @Min(value = 1, message = "Điểm giá trị tour tối thiểu là 1 sao")
        @Max(value = 5, message = "Điểm giá trị tour tối đa là 5 sao")
        Integer ratingValue,

        @Size(max = 1000, message = "Nhận xét tối đa 1000 ký tự")
        String comments
) {
}
