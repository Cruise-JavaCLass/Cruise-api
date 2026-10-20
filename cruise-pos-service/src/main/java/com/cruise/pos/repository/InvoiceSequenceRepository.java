package com.cruise.pos.repository;

import com.cruise.pos.entity.InvoiceSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, String> {

    /**
     * Khoá dòng bộ đếm để cấp số nguyên tử trên phạm vi toàn cụm.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InvoiceSequence s WHERE s.sequenceKey = :sequenceKey")
    Optional<InvoiceSequence> findByKeyWithLock(@Param("sequenceKey") String sequenceKey);
}
