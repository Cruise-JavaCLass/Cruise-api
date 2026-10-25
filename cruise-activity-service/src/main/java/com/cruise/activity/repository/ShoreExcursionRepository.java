package com.cruise.activity.repository;

import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShoreExcursionRepository extends JpaRepository<ShoreExcursion, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ShoreExcursion e WHERE e.id = :id")
    Optional<ShoreExcursion> findByIdWithLock(@Param("id") Long id);

    List<ShoreExcursion> findByTourIdAndActiveTrueOrderByExcursionDateAscGatheringTimeAsc(Long tourId);

    List<ShoreExcursion> findByTourIdAndPortIdAndActiveTrueOrderByGatheringTimeAsc(Long tourId, Long portId);

    List<ShoreExcursion> findByTourIdAndDayNumberAndActiveTrueOrderByGatheringTimeAsc(Long tourId, Integer dayNumber);

    List<ShoreExcursion> findByTourIdAndStatusAndActiveTrueOrderByGatheringTimeAsc(Long tourId, ExcursionStatus status);

    long countByExcursionDateAndActiveTrue(LocalDate excursionDate);

    @Query("SELECT e FROM ShoreExcursion e WHERE e.active = true AND e.status IN (:statuses) AND e.excursionDate >= :fromDate ORDER BY e.excursionDate ASC, e.departureTime ASC")
    List<ShoreExcursion> findLiveUpcomingExcursions(
            @Param("statuses") Collection<ExcursionStatus> statuses,
            @Param("fromDate") LocalDate fromDate
    );

    @Query("SELECT e FROM ShoreExcursion e WHERE e.tourId = :tourId AND e.active = true " +
            "AND (:portId IS NULL OR e.portId = :portId) " +
            "AND (:dayNumber IS NULL OR e.dayNumber = :dayNumber) " +
            "AND (:keyword IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "     OR LOWER(e.portName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "     OR LOWER(e.localProvider) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ShoreExcursion> findPagedExcursions(
            @Param("tourId") Long tourId,
            @Param("portId") Long portId,
            @Param("dayNumber") Integer dayNumber,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("SELECT COUNT(e) > 0 FROM ShoreExcursion e WHERE e.active = true " +
            "AND e.tourId = :tourId " +
            "AND e.portId = :portId " +
            "AND e.excursionDate = :excursionDate " +
            "AND (:excludeId IS NULL OR e.id <> :excludeId) " +
            "AND LOWER(e.title) = LOWER(:title)")
    boolean existsDuplicateExcursion(
            @Param("tourId") Long tourId,
            @Param("portId") Long portId,
            @Param("excursionDate") LocalDate excursionDate,
            @Param("title") String title,
            @Param("excludeId") Long excludeId
    );
}
