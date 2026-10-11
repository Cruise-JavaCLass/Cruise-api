package com.cruise.itinerary.repository;

import com.cruise.itinerary.entity.Port;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortRepository extends JpaRepository<Port, Long> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    Optional<Port> findByCodeIgnoreCase(String code);

    List<Port> findByActiveTrue();

    @Query("SELECT p FROM Port p WHERE " +
           "(:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.city) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:activeOnly IS NULL OR p.active = :activeOnly)")
    Page<Port> searchPorts(@Param("keyword") String keyword,
                           @Param("activeOnly") Boolean activeOnly,
                           Pageable pageable);
}
