package com.cruise.pos.repository;

import com.cruise.pos.entity.PosOutlet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PosOutletRepository extends JpaRepository<PosOutlet, Long> {
    /**
     * Finds an outlet by its catalog code.
     */
    Optional<PosOutlet> findByCode(String code);
    /**
     * Reports whether an outlet with the specified code exists.
     */
    boolean existsByCode(String code);
    /**
     * Returns all active POS outlets.
     */
    List<PosOutlet> findByIsActiveTrue();
}
