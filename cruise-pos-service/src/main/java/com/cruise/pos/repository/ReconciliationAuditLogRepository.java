package com.cruise.pos.repository;

import com.cruise.pos.entity.ReconciliationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReconciliationAuditLogRepository extends JpaRepository<ReconciliationAuditLog, Long> {
    List<ReconciliationAuditLog> findByTargetIdOrderByLoggedAtDesc(String targetId);
}
