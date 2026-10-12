package com.cruise.itinerary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReorderItineraryRequest {

    @NotEmpty(message = "Danh sách sắp xếp không được rỗng")
    @Valid
    private List<ReorderItemDto> items;
}
