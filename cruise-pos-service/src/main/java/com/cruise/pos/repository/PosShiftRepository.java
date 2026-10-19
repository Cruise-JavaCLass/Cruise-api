package com.cruise.pos.repository;

import com.cruise.pos.entity.PosShift;
import com.cruise.pos.enums.ShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PosShiftRepository extends JpaRepository<PosShift, Long> {

    Optional<PosShift> findByCashierIdAndStatus(String cashierId, ShiftStatus status);

    Optional<PosShift> findByStationIdAndStatus(String stationId, ShiftStatus status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM PosShift s WHERE s.stationId = :stationId AND s.status = :status")
    Optional<PosShift> findByStationIdAndStatusWithLock(
            @org.springframework.data.repository.query.Param("stationId") String stationId,
            @org.springframework.data.repository.query.Param("status") ShiftStatus status
    );

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM PosShift s WHERE s.id = :shiftId")
    Optional<PosShift> findByIdWithLock(
            @org.springframework.data.repository.query.Param("shiftId") Long shiftId
    );

    List<PosShift> findByStatus(ShiftStatus status);

    List<PosShift> findByCashierIdOrderByOpenedAtDesc(String cashierId);
}
