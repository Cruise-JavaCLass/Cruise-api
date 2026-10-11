package com.cruise.itinerary.repository;

import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.enums.TourStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository thao tác dữ liệu với bảng cruise_tours
 */
@Repository
public interface CruiseTourRepository extends JpaRepository<CruiseTour, Long> {

    Optional<CruiseTour> findByCode(String code);

    Optional<CruiseTour> findByName(String name);

    boolean existsByCode(String code);

    boolean existsByName(String name);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByNameAndIdNot(String name, Long id);

    List<CruiseTour> findByActiveTrue();

    List<CruiseTour> findByStatusAndActiveTrue(TourStatus status);

    List<CruiseTour> findByShipIdAndActiveTrue(Long shipId);

    @Query("SELECT t FROM CruiseTour t LEFT JOIN t.ship s WHERE " +
            "(:activeOnly IS NULL OR t.active = :activeOnly) AND " +
            "(:status IS NULL OR t.status = :status) AND " +
            "(:shipId IS NULL OR (s IS NOT NULL AND s.id = :shipId)) AND " +
            "(:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(t.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(t.departurePort) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(t.destinationPort) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<CruiseTour> searchTours(
            @Param("keyword") String keyword,
            @Param("status") TourStatus status,
            @Param("shipId") Long shipId,
            @Param("activeOnly") Boolean activeOnly,
            Pageable pageable
    );

    long countByActiveTrue();

    long countByStatusAndActiveTrue(TourStatus status);
}
