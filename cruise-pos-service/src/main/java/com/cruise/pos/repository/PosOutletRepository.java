package com.cruise.pos.repository;

import com.cruise.pos.entity.PosOutlet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PosOutletRepository extends JpaRepository<PosOutlet, Long> {
    Optional<PosOutlet> findByCode(String code);
    boolean existsByCode(String code);
    List<PosOutlet> findByIsActiveTrue();
}
