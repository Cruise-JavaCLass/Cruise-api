package com.cruise.pos.repository;

import com.cruise.pos.entity.PosCashDrop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PosCashDropRepository extends JpaRepository<PosCashDrop, Long> {

    List<PosCashDrop> findByShiftIdOrderByDroppedAtAsc(Long shiftId);

    Optional<PosCashDrop> findByReceiptNumber(String receiptNumber);
}
