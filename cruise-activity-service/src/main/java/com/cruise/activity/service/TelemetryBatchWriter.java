package com.cruise.activity.service;

import com.cruise.activity.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class TelemetryBatchWriter {
    private final PassengerLocationLogRepository history;
    private final BleBeaconDeviceRepository beacons;
    @Transactional
    public void write(List<TelemetryBuffer.Sample> batch) {
        history.saveAll(batch.stream().map(TelemetryBuffer.Sample::location).toList());
        Map<Long,Instant> heartbeats = new HashMap<>();
        batch.forEach(sample -> heartbeats.merge(sample.beaconId(),sample.location().getRecordedAt(),(a,b)->a.isAfter(b)?a:b));
        heartbeats.forEach(beacons::updateHeartbeatIfNewer);
    }
}
