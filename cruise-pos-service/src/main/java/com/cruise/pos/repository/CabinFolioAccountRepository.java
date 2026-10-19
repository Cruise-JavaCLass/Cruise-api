package com.cruise.pos.repository;

import com.cruise.pos.entity.CabinFolioAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CabinFolioAccountRepository extends JpaRepository<CabinFolioAccount, Long> {

    Optional<CabinFolioAccount> findByCabinNumber(String cabinNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM CabinFolioAccount f WHERE f.cabinNumber = :cabinNumber")
    Optional<CabinFolioAccount> findByCabinNumberWithLock(@Param("cabinNumber") String cabinNumber);

    Optional<CabinFolioAccount> findByPassengerId(Long passengerId);
}
