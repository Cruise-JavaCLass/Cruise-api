package com.cruise.pos.repository;

import com.cruise.pos.entity.ReconciliationBatch;
import com.cruise.pos.enums.BatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReconciliationBatchRepository extends JpaRepository<ReconciliationBatch, Long> {
    Optional<ReconciliationBatch> findByBatchNumber(String batchNumber);
    Page<ReconciliationBatch> findByStatus(BatchStatus status, Pageable pageable);
    boolean existsByStatus(BatchStatus status);
}
