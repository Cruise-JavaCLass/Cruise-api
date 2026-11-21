package com.cruise.activity.repository;
import com.cruise.activity.entity.BleBeaconDevice;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BleBeaconDeviceRepository extends JpaRepository<BleBeaconDevice,Long> {
 java.util.Optional<BleBeaconDevice> findByMacAddress(String mac);
 @org.springframework.data.jpa.repository.Modifying
 @org.springframework.data.jpa.repository.Query("update BleBeaconDevice b set b.lastHeartbeat=:timestamp where b.id=:id and (b.lastHeartbeat is null or b.lastHeartbeat<:timestamp)")
 int updateHeartbeatIfNewer(@org.springframework.data.repository.query.Param("id") Long id,
     @org.springframework.data.repository.query.Param("timestamp") java.time.Instant timestamp);
}
