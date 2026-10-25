package com.cruise.pos.repository;

import com.cruise.pos.entity.ServiceRecoveryObcVoucher;
import com.cruise.pos.enums.ObcVoucherStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRecoveryObcVoucherRepository extends JpaRepository<ServiceRecoveryObcVoucher, Long> {

    Optional<ServiceRecoveryObcVoucher> findByVoucherCode(String voucherCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ServiceRecoveryObcVoucher v WHERE v.voucherCode = :voucherCode")
    Optional<ServiceRecoveryObcVoucher> findByVoucherCodeWithLock(@Param("voucherCode") String voucherCode);

    boolean existsByVoucherCode(String voucherCode);

    List<ServiceRecoveryObcVoucher> findByCaseId(Long caseId);

    List<ServiceRecoveryObcVoucher> findByCabinNumberOrderByCreatedAtDesc(String cabinNumber);

    List<ServiceRecoveryObcVoucher> findByPassengerIdOrderByCreatedAtDesc(Long passengerId);

    List<ServiceRecoveryObcVoucher> findByPassengerIdAndStatus(Long passengerId, ObcVoucherStatus status);
}
