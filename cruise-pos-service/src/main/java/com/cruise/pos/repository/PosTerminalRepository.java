package com.cruise.pos.repository;

import com.cruise.pos.entity.PosTerminal;
import com.cruise.pos.enums.PosTerminalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PosTerminalRepository extends JpaRepository<PosTerminal, Long> {
    Optional<PosTerminal> findByDeviceId(String deviceId);
    Optional<PosTerminal> findByApiKeyHash(String apiKeyHash);
    Page<PosTerminal> findByStatus(PosTerminalStatus status, Pageable pageable);
    Page<PosTerminal> findByStationId(String stationId, Pageable pageable);
    boolean existsByDeviceId(String deviceId);
}
