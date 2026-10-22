package com.cruise.pos.repository;

import com.cruise.pos.entity.PosCredential;
import com.cruise.pos.enums.CredentialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PosCredentialRepository extends JpaRepository<PosCredential, Long> {

    Optional<PosCredential> findByIdentifier(String identifier);

    List<PosCredential> findByPassengerId(Long passengerId);

    List<PosCredential> findByCabinNumber(String cabinNumber);

    List<PosCredential> findByStatus(CredentialStatus status);

    @Query("SELECT c FROM PosCredential c WHERE c.status = :status ORDER BY c.id ASC")
    List<PosCredential> findAllByStatusActive(@Param("status") CredentialStatus status);
}
