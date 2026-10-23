package com.cruise.itinerary.repository;

import com.cruise.itinerary.entity.ShipDeckZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShipDeckZoneRepository extends JpaRepository<ShipDeckZone, Long> {
    List<ShipDeckZone> findByShipIdOrderByDeckNumberAsc(Long shipId);
    List<ShipDeckZone> findByShipIdAndDeckNumber(Long shipId, Integer deckNumber);
}
