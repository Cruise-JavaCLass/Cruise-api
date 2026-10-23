package com.cruise.pos.repository;

import com.cruise.pos.entity.SystemAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SystemAuditLogRepository extends JpaRepository<SystemAuditLog, Long> {

    @Query("SELECT a FROM SystemAuditLog a WHERE " +
           "(:category IS NULL OR a.category = :category) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:actor IS NULL OR LOWER(a.actor) LIKE LOWER(CONCAT('%', :actor, '%'))) AND " +
           "(:targetId IS NULL OR a.targetId = :targetId) AND " +
           "(:fromDate IS NULL OR a.createdAt >= :fromDate) AND " +
           "(:toDate IS NULL OR a.createdAt <= :toDate) " +
           "ORDER BY a.createdAt DESC")
    Page<SystemAuditLog> searchAuditLogs(
            @Param("category") String category,
            @Param("action") String action,
            @Param("actor") String actor,
            @Param("targetId") String targetId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            Pageable pageable
    );

    @Query("SELECT a FROM SystemAuditLog a WHERE " +
           "(:category IS NULL OR a.category = :category) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:actor IS NULL OR LOWER(a.actor) LIKE LOWER(CONCAT('%', :actor, '%'))) AND " +
           "(:targetId IS NULL OR a.targetId = :targetId) " +
           "ORDER BY a.createdAt DESC")
    List<SystemAuditLog> exportAuditLogs(
            @Param("category") String category,
            @Param("action") String action,
            @Param("actor") String actor,
            @Param("targetId") String targetId,
            Pageable pageable
    );
}
