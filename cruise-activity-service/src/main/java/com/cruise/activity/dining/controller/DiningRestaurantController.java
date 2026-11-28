package com.cruise.activity.dining.controller;

import com.cruise.activity.dining.entity.Restaurant;
import com.cruise.activity.dining.service.DiningRestaurantService;
import com.cruise.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dining/restaurants")
@RequiredArgsConstructor
public class DiningRestaurantController {

    private final DiningRestaurantService restaurantService;

    @GetMapping
    public ApiResponse<Page<Restaurant>> getRestaurants(
            @RequestParam(required = false) String deck,
            @RequestParam(required = false) String cuisine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);

        return ApiResponse.ok(
                restaurantService.searchRestaurants(deck, cuisine, pageable)
        );
    }
}
