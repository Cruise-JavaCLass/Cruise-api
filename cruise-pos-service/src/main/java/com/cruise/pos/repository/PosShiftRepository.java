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

    List<PosShift> findByStatus(ShiftStatus status);

    List<PosShift> findByCashierIdOrderByOpenedAtDesc(String cashierId);
}
