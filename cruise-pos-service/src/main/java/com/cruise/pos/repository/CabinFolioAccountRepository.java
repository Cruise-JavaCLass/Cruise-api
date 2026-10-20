package com.cruise.pos.repository;

import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.enums.FolioStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Page<CabinFolioAccount> findByStatus(FolioStatus status, Pageable pageable);

    @Query("SELECT f FROM CabinFolioAccount f WHERE " +
           "(:search IS NULL OR LOWER(f.cabinNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(f.primaryGuestName) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:status IS NULL OR f.status = :status)")
    Page<CabinFolioAccount> findWithFilter(@Param("search") String search, @Param("status") FolioStatus status, Pageable pageable);
}
