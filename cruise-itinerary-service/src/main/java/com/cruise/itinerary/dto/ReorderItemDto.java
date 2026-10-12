package com.cruise.itinerary.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReorderItemDto {

    @NotNull(message = "ID mục lịch trình không được để trống")
    private Long id;

    @NotNull(message = "Thứ tự mới không được để trống")
    @Positive(message = "Thứ tự mới phải là số nguyên dương")
    private Integer newOrder;
}
