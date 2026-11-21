package com.cruise.pos.repository;

import com.cruise.pos.entity.PreAuthorizationHold;
import com.cruise.pos.enums.PreAuthHoldStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PreAuthorizationHoldRepository extends JpaRepository<PreAuthorizationHold, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM PreAuthorizationHold h WHERE h.id = :id")
    Optional<PreAuthorizationHold> findByIdForUpdate(@Param("id") Long id);

    List<PreAuthorizationHold> findByFolio_IdAndStatus(Long folioId, PreAuthHoldStatus status);

    /** Tổng tiền các khoản tạm giữ còn hiệu lực (đúng trạng thái và chưa hết hạn); trả null nếu không có */
    @Query("SELECT SUM(h.holdAmount) FROM PreAuthorizationHold h "
            + "WHERE h.folio.id = :folioId AND h.status = :status AND h.expiresAt > :now")
    BigDecimal sumActiveHoldAmount(@Param("folioId") Long folioId,
                                   @Param("status") PreAuthHoldStatus status,
                                   @Param("now") Instant now);
}
