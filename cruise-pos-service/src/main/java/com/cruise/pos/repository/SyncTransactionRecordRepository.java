package com.cruise.pos.repository;
import com.cruise.pos.entity.SyncTransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SyncTransactionRecordRepository extends JpaRepository<SyncTransactionRecord,Long> { java.util.List<SyncTransactionRecord> findByBatchIdOrderByRecordPositionAsc(Long batchId); java.util.List<SyncTransactionRecord> findByIdempotencyKey(String key); }
