package com.cruise.pos.repository;
import com.cruise.pos.entity.SyncBatch;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SyncBatchRepository extends JpaRepository<SyncBatch,Long> { java.util.Optional<SyncBatch> findByTerminalIdAndBatchNumber(String terminalId,String batchNumber); }
