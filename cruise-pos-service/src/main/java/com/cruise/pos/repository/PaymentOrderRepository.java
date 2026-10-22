package com.cruise.pos.repository;

import com.cruise.pos.entity.PaymentOrder;
import com.cruise.pos.enums.PaymentGatewayStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByPaymentReference(String paymentReference);

    Optional<PaymentOrder> findByIdempotencyKey(String idempotencyKey);

    List<PaymentOrder> findByCabinNumberAndStatus(String cabinNumber, PaymentGatewayStatus status);
}
