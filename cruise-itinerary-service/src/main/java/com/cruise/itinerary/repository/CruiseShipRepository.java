package com.cruise.itinerary.repository;

import com.cruise.itinerary.entity.CruiseShip;
import com.cruise.itinerary.enums.ShipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository thao tác dữ liệu với bảng cruise_ships
 */
@Repository
public interface CruiseShipRepository extends JpaRepository<CruiseShip, Long> {

    Optional<CruiseShip> findByCode(String code);

    Optional<CruiseShip> findByName(String name);

    boolean existsByCode(String code);

    boolean existsByName(String name);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByNameAndIdNot(String name, Long id);

    List<CruiseShip> findByStatus(ShipStatus status);

    List<CruiseShip> findByActiveTrue();

    List<CruiseShip> findByStatusAndActiveTrue(ShipStatus status);

    List<CruiseShip> findByOperatorIgnoreCase(String operator);

    Page<CruiseShip> findByActiveTrue(Pageable pageable);

    Page<CruiseShip> findByStatusAndActiveTrue(ShipStatus status, Pageable pageable);

    @Query("SELECT s FROM CruiseShip s WHERE " +
            "(:activeOnly IS NULL OR s.active = :activeOnly) AND " +
            "(:status IS NULL OR s.status = :status) AND " +
            "(:keyword IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(s.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(s.operator) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<CruiseShip> searchShips(
            @Param("keyword") String keyword,
            @Param("status") ShipStatus status,
            @Param("activeOnly") Boolean activeOnly,
            Pageable pageable
    );

    long countByActiveTrue();

    long countByStatusAndActiveTrue(ShipStatus status);
}
