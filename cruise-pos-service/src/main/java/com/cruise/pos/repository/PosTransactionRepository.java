package com.cruise.pos.repository;

import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PosTransactionRepository extends JpaRepository<PosTransaction, Long>, JpaSpecificationExecutor<PosTransaction> {

    Optional<PosTransaction> findByTransactionUuid(String transactionUuid);

    Optional<PosTransaction> findByIdempotencyKey(String idempotencyKey);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PosTransaction t WHERE t.id = :id")
    Optional<PosTransaction> findByIdWithLock(@Param("id") Long id);

    Page<PosTransaction> findByShiftId(Long shiftId, Pageable pageable);

    List<PosTransaction> findByShiftId(Long shiftId);

    List<PosTransaction> findByCabinNumber(String cabinNumber);

    List<PosTransaction> findByOriginalTransactionId(Long originalTransactionId);

    @Query("SELECT t FROM PosTransaction t WHERE " +
            "(:stationId IS NULL OR t.stationId = :stationId) AND " +
            "(:cashierId IS NULL OR t.cashierId = :cashierId) AND " +
            "(:status IS NULL OR t.status = :status) AND " +
            "(:paymentMethod IS NULL OR t.paymentMethod = :paymentMethod) AND " +
            "(:cabinNumber IS NULL OR t.cabinNumber = :cabinNumber) AND " +
            "(:startDate IS NULL OR t.createdAt >= :startDate) AND " +
            "(:endDate IS NULL OR t.createdAt <= :endDate)")
    Page<PosTransaction> findWithFilters(
            @Param("stationId") String stationId,
            @Param("cashierId") String cashierId,
            @Param("status") TransactionStatus status,
            @Param("paymentMethod") PaymentMethod paymentMethod,
            @Param("cabinNumber") String cabinNumber,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );
}
