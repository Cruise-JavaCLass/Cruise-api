package com.cruise.pos.repository;
import com.cruise.pos.entity.SyncConflictLog;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SyncConflictLogRepository extends JpaRepository<SyncConflictLog,Long> { org.springframework.data.domain.Page<SyncConflictLog> findByResolutionStatus(String status,org.springframework.data.domain.Pageable page);
 long countByBatchIdAndResolutionStatus(Long batchId,String status);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select c from SyncConflictLog c where c.id=:id")
 java.util.Optional<SyncConflictLog> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id); }
