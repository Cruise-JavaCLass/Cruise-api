package com.cruise.pos.repository;

import com.cruise.pos.entity.FolioDispute;
import com.cruise.pos.enums.FolioDisputeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface FolioDisputeRepository extends JpaRepository<FolioDispute, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM FolioDispute d WHERE d.id = :id")
    Optional<FolioDispute> findByIdForUpdate(@Param("id") Long id);

    boolean existsByTransaction_IdAndStatusIn(Long transactionId, Collection<FolioDisputeStatus> statuses);

    /** Tổng số tiền khiếu nại theo giao dịch và trạng thái; trả null nếu không có */
    @Query("SELECT SUM(d.disputedAmount) FROM FolioDispute d "
            + "WHERE d.transaction.id = :transactionId AND d.status IN :statuses")
    BigDecimal sumDisputedAmount(@Param("transactionId") Long transactionId,
                                 @Param("statuses") Collection<FolioDisputeStatus> statuses);
}
