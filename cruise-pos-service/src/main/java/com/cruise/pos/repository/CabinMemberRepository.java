package com.cruise.pos.repository;

import com.cruise.pos.entity.CabinMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CabinMemberRepository extends JpaRepository<CabinMember, Long> {
    List<CabinMember> findByCabinNumber(String cabinNumber);
    Optional<CabinMember> findByCabinNumberAndPassengerId(String cabinNumber, Long passengerId);
    Optional<CabinMember> findByPassengerId(Long passengerId);
    boolean existsByCabinNumber(String cabinNumber);
}
