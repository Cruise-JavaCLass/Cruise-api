package com.cruise.activity.repository;

import com.cruise.activity.entity.ExcursionPassenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository quản lý dữ liệu hành khách tour bờ (KAN-176)
 */
@Repository
public interface ExcursionPassengerRepository extends JpaRepository<ExcursionPassenger, Long> {

    List<ExcursionPassenger> findByExcursionIdAndActiveTrue(Long excursionId);

    Optional<ExcursionPassenger> findByIdAndExcursionIdAndActiveTrue(Long id, Long excursionId);

    List<ExcursionPassenger> findByExcursionIdAndIdInAndActiveTrue(Long excursionId, List<Long> ids);

    boolean existsByTicketCode(String ticketCode);

    long countByExcursionIdAndActiveTrue(Long excursionId);
}
