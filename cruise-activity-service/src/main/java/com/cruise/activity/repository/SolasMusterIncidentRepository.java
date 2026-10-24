package com.cruise.activity.repository;

import com.cruise.activity.entity.SolasMusterIncident;
import com.cruise.activity.enums.MusterStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface SolasMusterIncidentRepository extends JpaRepository<SolasMusterIncident, Long> {

    Optional<SolasMusterIncident> findFirstByStatusInOrderByCreatedAtDesc(Collection<MusterStatus> statuses);

    Optional<SolasMusterIncident> findByIncidentCode(String incidentCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM SolasMusterIncident i WHERE i.id = :id")
    Optional<SolasMusterIncident> findByIdForUpdate(@Param("id") Long id);
}
