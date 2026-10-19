package com.cruise.pos.repository;

import com.cruise.pos.entity.DiscrepancyRecord;
import com.cruise.pos.enums.DiscrepancyStatus;
import com.cruise.pos.enums.DiscrepancyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiscrepancyRecordRepository extends JpaRepository<DiscrepancyRecord, Long> {
    Page<DiscrepancyRecord> findByStatus(DiscrepancyStatus status, Pageable pageable);
    Page<DiscrepancyRecord> findByBatchId(Long batchId, Pageable pageable);
    List<DiscrepancyRecord> findByBatchId(Long batchId);
    long countByStatus(DiscrepancyStatus status);
    List<DiscrepancyRecord> findByTransactionId(String transactionId);
}
