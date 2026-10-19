package com.cruise.pos.repository;

import com.cruise.pos.entity.PosTransactionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PosTransactionItemRepository extends JpaRepository<PosTransactionItem, Long> {
    List<PosTransactionItem> findByTransactionId(Long transactionId);
}
