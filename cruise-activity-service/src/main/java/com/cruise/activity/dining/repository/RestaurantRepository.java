package com.cruise.activity.dining.repository;

import com.cruise.activity.dining.entity.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Restaurant r where r.id=:id")
    java.util.Optional<Restaurant> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT r FROM Restaurant r
        WHERE (:location IS NULL OR LOWER(r.location) = LOWER(:location))
          AND (:cuisineType IS NULL OR LOWER(r.cuisineType) = LOWER(:cuisineType))
        """)
    Page<Restaurant> searchRestaurants(
            @Param("location") String location,
            @Param("cuisineType") String cuisineType,
            Pageable pageable
    );
}
