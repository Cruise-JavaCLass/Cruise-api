package com.cruise.pos.repository;

import com.cruise.pos.entity.PosIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PosIdempotencyRepository extends JpaRepository<PosIdempotencyRecord, Long> {
    Optional<PosIdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
}
