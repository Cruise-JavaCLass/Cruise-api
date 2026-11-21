package com.cruise.activity.repository;
import com.cruise.activity.entity.MusterStation;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MusterStationRepository extends JpaRepository<MusterStation,Long> { java.util.Optional<MusterStation> findByStationCode(String code); }
