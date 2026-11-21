package com.cruise.activity.repository;
import com.cruise.activity.entity.DeckZone;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DeckZoneRepository extends JpaRepository<DeckZone,Long> { java.util.Optional<DeckZone> findByZoneCode(String code); }
