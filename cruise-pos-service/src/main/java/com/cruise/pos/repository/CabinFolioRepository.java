package com.cruise.pos.repository;

import com.cruise.pos.entity.CabinFolio;
import com.cruise.pos.enums.CabinFolioStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface CabinFolioRepository extends JpaRepository<CabinFolio, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM CabinFolio f WHERE f.id = :id")
    Optional<CabinFolio> findByIdForUpdate(@Param("id") Long id);

    Optional<CabinFolio> findFirstByCabinNumberAndStatusInOrderByIdDesc(String cabinNumber,
                                                                         Collection<CabinFolioStatus> statuses);
}
