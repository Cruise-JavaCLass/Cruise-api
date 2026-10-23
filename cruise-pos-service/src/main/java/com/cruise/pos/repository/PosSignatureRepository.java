package com.cruise.pos.repository;

import com.cruise.pos.entity.PosSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PosSignatureRepository extends JpaRepository<PosSignature, Long> {
    Optional<PosSignature> findByTransactionUuid(String transactionUuid);
    Optional<PosSignature> findByTransactionId(Long transactionId);
    boolean existsByTransactionUuid(String transactionUuid);
}
