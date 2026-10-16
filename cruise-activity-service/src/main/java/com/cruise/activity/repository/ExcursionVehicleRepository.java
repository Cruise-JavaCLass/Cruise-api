package com.cruise.activity.repository;

import com.cruise.activity.entity.ExcursionVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository quản lý dữ liệu phương tiện tour bờ (KAN-177)
 */
@Repository
public interface ExcursionVehicleRepository extends JpaRepository<ExcursionVehicle, Long> {

    List<ExcursionVehicle> findByExcursionIdAndActiveTrue(Long excursionId);

    Optional<ExcursionVehicle> findByIdAndExcursionIdAndActiveTrue(Long id, Long excursionId);

    Optional<ExcursionVehicle> findByVehicleCodeAndExcursionIdAndActiveTrue(String vehicleCode, Long excursionId);

    long countByExcursionIdAndActiveTrue(Long excursionId);
}
