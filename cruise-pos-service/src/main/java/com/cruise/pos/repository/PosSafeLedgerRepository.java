package com.cruise.pos.repository;

import com.cruise.pos.entity.PosSafeLedger;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PosSafeLedgerRepository extends JpaRepository<PosSafeLedger, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PosSafeLedger s WHERE s.safeId = :safeId")
    Optional<PosSafeLedger> findByIdWithLock(@Param("safeId") String safeId);
}
