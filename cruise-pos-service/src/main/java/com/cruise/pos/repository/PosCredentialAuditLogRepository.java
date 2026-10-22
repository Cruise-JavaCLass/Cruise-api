package com.cruise.pos.repository;

import com.cruise.pos.entity.PosCredentialAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PosCredentialAuditLogRepository extends JpaRepository<PosCredentialAuditLog, Long> {

    List<PosCredentialAuditLog> findByStationId(String stationId);

    List<PosCredentialAuditLog> findByCashierId(String cashierId);

    Page<PosCredentialAuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
