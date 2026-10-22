package com.cruise.activity.repository;

import com.cruise.activity.entity.ProviderSettlement;
import com.cruise.activity.enums.SettlementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho Provider Settlement (KAN-332, KAN-333)
 */
@Repository
public interface ProviderSettlementRepository extends JpaRepository<ProviderSettlement, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProviderSettlement s WHERE s.id = :id")
    Optional<ProviderSettlement> findByIdWithLock(@Param("id") Long id);

    Optional<ProviderSettlement> findByExcursionIdAndProviderId(Long excursionId, String providerId);

    List<ProviderSettlement> findByVoyageId(Long voyageId);

    List<ProviderSettlement> findByProviderId(String providerId);

    List<ProviderSettlement> findByProviderIdAndVoyageId(String providerId, Long voyageId);

    Page<ProviderSettlement> findByStatus(SettlementStatus status, Pageable pageable);

    @Query("SELECT s FROM ProviderSettlement s WHERE " +
            "(:providerId IS NULL OR s.providerId = :providerId) AND " +
            "(:voyageId IS NULL OR s.voyageId = :voyageId) AND " +
            "(:status IS NULL OR s.status = :status)")
    Page<ProviderSettlement> searchSettlements(
            @Param("providerId") String providerId,
            @Param("voyageId") Long voyageId,
            @Param("status") SettlementStatus status,
            Pageable pageable
    );
}
