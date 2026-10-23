package com.cruise.pos.repository;

import com.cruise.pos.entity.PaymentOrder;
import com.cruise.pos.enums.PaymentGatewayStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByPaymentReference(String paymentReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentOrder p WHERE p.paymentReference = :paymentReference")
    Optional<PaymentOrder> findByPaymentReferenceForUpdate(@Param("paymentReference") String paymentReference);

    Optional<PaymentOrder> findByIdempotencyKey(String idempotencyKey);

    List<PaymentOrder> findByCabinNumberAndStatus(String cabinNumber, PaymentGatewayStatus status);
}
