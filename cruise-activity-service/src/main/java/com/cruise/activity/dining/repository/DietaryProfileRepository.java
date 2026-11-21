package com.cruise.activity.dining.repository;

import com.cruise.activity.dining.entity.GuestDietaryProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DietaryProfileRepository
        extends JpaRepository<GuestDietaryProfile, Long> {

    List<GuestDietaryProfile> findByPassengerId(Long passengerId);
}