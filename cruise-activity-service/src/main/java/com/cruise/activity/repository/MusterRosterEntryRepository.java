package com.cruise.activity.repository;
import com.cruise.activity.entity.MusterRosterEntry;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MusterRosterEntryRepository extends JpaRepository<MusterRosterEntry,Long> {
 java.util.List<MusterRosterEntry> findByIncidentIdOrderByPassengerIdAsc(Long incidentId);
 java.util.Optional<MusterRosterEntry> findByIncidentIdAndPassengerId(Long incidentId,Long passengerId);
 @org.springframework.data.jpa.repository.Query("select count(c) from SolasMusterCheckin c join MusterRosterEntry r on r.incidentId=c.incidentId and r.passengerId=c.passengerId where c.incidentId=:id and c.musterStationCode=r.stationCode")
 long countCorrectArrivals(@org.springframework.data.repository.query.Param("id") Long id);
}
