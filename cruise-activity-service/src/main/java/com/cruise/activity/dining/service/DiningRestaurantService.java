package com.cruise.activity.dining.service;

import com.cruise.activity.dining.entity.Restaurant;
import com.cruise.activity.dining.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiningRestaurantService {

    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public Page<Restaurant> searchRestaurants(
            String location,
            String cuisineType,
            Pageable pageable
    ) {
        return restaurantRepository.searchRestaurants(
                location,
                cuisineType,
                pageable
        );
    }
}
