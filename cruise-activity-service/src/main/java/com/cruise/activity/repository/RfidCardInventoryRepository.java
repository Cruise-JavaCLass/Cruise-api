package com.cruise.activity.repository;

import com.cruise.activity.entity.RfidCardInventory;
import com.cruise.activity.enums.RfidCardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RfidCardInventoryRepository extends JpaRepository<RfidCardInventory, Long> {

    Optional<RfidCardInventory> findByCardUid(String cardUid);

    List<RfidCardInventory> findByBatchNumber(String batchNumber);

    Page<RfidCardInventory> findByStatus(RfidCardStatus status, Pageable pageable);

    List<RfidCardInventory> findByStatus(RfidCardStatus status);

    Optional<RfidCardInventory> findByAssignedPassengerId(Long passengerId);

    @Query("SELECT r.cardUid FROM RfidCardInventory r WHERE r.status = 'BLACKLISTED' OR r.status = 'LOST_REPORTED'")
    List<String> findAllActiveBlacklistedUids();

    @Query("SELECT COUNT(r) FROM RfidCardInventory r WHERE r.status = :status")
    long countByStatus(@Param("status") RfidCardStatus status);
}
