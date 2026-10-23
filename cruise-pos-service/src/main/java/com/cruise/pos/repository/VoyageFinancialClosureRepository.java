package com.cruise.pos.repository;

import com.cruise.pos.entity.VoyageFinancialClosure;
import com.cruise.pos.enums.VoyageFinancialStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoyageFinancialClosureRepository extends JpaRepository<VoyageFinancialClosure, Long> {

    Optional<VoyageFinancialClosure> findByVoyageNumber(String voyageNumber);

    List<VoyageFinancialClosure> findByShipIdOrderByVoyageEndDateDesc(String shipId);

    @Query("SELECT v FROM VoyageFinancialClosure v WHERE " +
           "(:status IS NULL OR v.status = :status) AND " +
           "(:shipId IS NULL OR v.shipId = :shipId) AND " +
           "(:search IS NULL OR LOWER(v.voyageNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
           " OR LOWER(v.itineraryName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY v.voyageEndDate DESC")
    Page<VoyageFinancialClosure> searchVoyages(
            @Param("status") VoyageFinancialStatus status,
            @Param("shipId") String shipId,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT v FROM VoyageFinancialClosure v WHERE v.status IN :statuses ORDER BY v.voyageEndDate DESC")
    List<VoyageFinancialClosure> findRecentClosedVoyages(
            @Param("statuses") Collection<VoyageFinancialStatus> statuses,
            Pageable pageable
    );
}
