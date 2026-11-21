package com.cruise.pos.repository;
import com.cruise.pos.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord,Long> { java.util.Optional<IdempotencyRecord> findByIdempotencyKey(String key); java.util.Optional<IdempotencyRecord> findByTransactionReference(String reference); }
