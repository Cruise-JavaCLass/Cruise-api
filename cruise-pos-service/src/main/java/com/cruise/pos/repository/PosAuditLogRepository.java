package com.cruise.pos.repository;

import com.cruise.pos.entity.PosAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PosAuditLogRepository extends JpaRepository<PosAuditLog, Long> {
    List<PosAuditLog> findByTransactionIdOrderByCreatedAtDesc(Long transactionId);
}
