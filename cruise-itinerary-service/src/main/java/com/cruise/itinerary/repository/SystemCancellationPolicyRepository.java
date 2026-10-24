package com.cruise.itinerary.repository;

import com.cruise.itinerary.entity.SystemCancellationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemCancellationPolicyRepository extends JpaRepository<SystemCancellationPolicy, Long> {
    List<SystemCancellationPolicy> findByServiceTypeAndIsActiveTrueOrderByHoursBeforeCutoffDesc(String serviceType);
}
