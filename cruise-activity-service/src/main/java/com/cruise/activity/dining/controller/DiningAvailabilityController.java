package com.cruise.activity.dining.controller;

import com.cruise.activity.dining.dto.TableAvailabilityResponse;
import com.cruise.activity.dining.entity.DiningTable;
import com.cruise.activity.dining.repository.DiningTableRepository;
import com.cruise.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dining/availability")
@RequiredArgsConstructor
public class DiningAvailabilityController {

    private final DiningTableRepository tableRepository;

    @GetMapping
    public ApiResponse<List<TableAvailabilityResponse>> checkAvailability(
            @RequestParam Long restaurantId,
            @RequestParam LocalDateTime dateTime,
            @RequestParam Integer guestCount
    ) {
        List<TableAvailabilityResponse> tables =
                tableRepository.findAvailableTables(
                        restaurantId, dateTime, guestCount
                ).stream()
                .map(this::toResponse)
                .toList();

        return ApiResponse.ok(tables);
    }

    private TableAvailabilityResponse toResponse(DiningTable table) {
        return TableAvailabilityResponse.builder()
                .tableId(table.getId())
                .tableNumber(table.getTableNumber())
                .capacity(table.getCapacity())
                .tableType(table.getTableType().name())
                .restaurantId(table.getRestaurant().getId())
                .restaurantName(table.getRestaurant().getName())
                .build();
    }
}
