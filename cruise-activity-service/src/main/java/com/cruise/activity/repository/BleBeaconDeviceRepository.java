package com.cruise.activity.repository;
import com.cruise.activity.entity.BleBeaconDevice;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BleBeaconDeviceRepository extends JpaRepository<BleBeaconDevice,Long> { java.util.Optional<BleBeaconDevice> findByMacAddress(String mac); }
