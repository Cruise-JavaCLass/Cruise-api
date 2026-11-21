package com.cruise.activity.repository;
import com.cruise.activity.entity.PassengerLocationLog;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PassengerLocationLogRepository extends JpaRepository<PassengerLocationLog,Long> { java.util.Optional<PassengerLocationLog> findFirstByPassengerIdOrderByRecordedAtDescIdDesc(Long id);
 @org.springframework.data.jpa.repository.Query("select l from PassengerLocationLog l where l.passengerId in :ids and not exists (select n.id from PassengerLocationLog n where n.passengerId=l.passengerId and (n.recordedAt>l.recordedAt or (n.recordedAt=l.recordedAt and n.id>l.id)))")
 java.util.List<PassengerLocationLog> findLatestForPassengers(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids); }
