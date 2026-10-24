package com.cruise.pos.repository;

import com.cruise.pos.entity.VoyageLifecycleState;
import com.cruise.pos.enums.VoyageOperationalPhase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoyageLifecycleStateRepository extends JpaRepository<VoyageLifecycleState, Long> {

    Optional<VoyageLifecycleState> findByVoyageNumber(String voyageNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM VoyageLifecycleState v WHERE v.voyageNumber = :voyageNumber")
    Optional<VoyageLifecycleState> findByVoyageNumberWithLock(@Param("voyageNumber") String voyageNumber);

    List<VoyageLifecycleState> findByShipIdOrderByCreatedAtDesc(String shipId);

    List<VoyageLifecycleState> findByCurrentPhase(VoyageOperationalPhase phase);

    List<VoyageLifecycleState> findByCurrentPhaseNot(VoyageOperationalPhase phase);

    boolean existsByVoyageNumber(String voyageNumber);
}
