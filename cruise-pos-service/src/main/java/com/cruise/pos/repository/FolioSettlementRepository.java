package com.cruise.pos.repository;

import com.cruise.pos.entity.FolioSettlement;
import com.cruise.pos.enums.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FolioSettlementRepository extends JpaRepository<FolioSettlement, Long> {

    Optional<FolioSettlement> findByIdempotencyKey(String idempotencyKey);

    Optional<FolioSettlement> findBySettlementUuid(String settlementUuid);

    Optional<FolioSettlement> findFirstByCabinNumberOrderBySettledAtDesc(String cabinNumber);

    boolean existsByCabinNumberAndStatus(String cabinNumber, SettlementStatus status);

    long countByCabinNumber(String cabinNumber);
}
