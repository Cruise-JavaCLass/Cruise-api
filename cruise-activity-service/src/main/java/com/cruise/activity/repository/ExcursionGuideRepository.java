package com.cruise.activity.repository;

import com.cruise.activity.entity.ExcursionGuide;
import com.cruise.activity.enums.GuideRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository quản lý dữ liệu hướng dẫn viên tour bờ (KAN-177)
 */
@Repository
public interface ExcursionGuideRepository extends JpaRepository<ExcursionGuide, Long> {

    List<ExcursionGuide> findByExcursionIdAndActiveTrue(Long excursionId);

    Optional<ExcursionGuide> findByIdAndExcursionIdAndActiveTrue(Long id, Long excursionId);

    Optional<ExcursionGuide> findByGuideCodeAndExcursionIdAndActiveTrue(String guideCode, Long excursionId);

    List<ExcursionGuide> findByExcursionIdAndRoleAndActiveTrue(Long excursionId, GuideRole role);

    long countByExcursionIdAndActiveTrue(Long excursionId);

    long countByExcursionId(Long excursionId);
}
