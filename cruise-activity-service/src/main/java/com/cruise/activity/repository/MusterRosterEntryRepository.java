package com.cruise.activity.repository;
import com.cruise.activity.entity.MusterRosterEntry;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MusterRosterEntryRepository extends JpaRepository<MusterRosterEntry,Long> { java.util.List<MusterRosterEntry> findByIncidentIdOrderByPassengerIdAsc(Long incidentId); java.util.Optional<MusterRosterEntry> findByIncidentIdAndPassengerId(Long incidentId,Long passengerId); }
