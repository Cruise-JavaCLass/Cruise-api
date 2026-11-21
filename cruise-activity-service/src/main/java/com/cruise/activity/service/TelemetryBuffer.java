package com.cruise.activity.service;

import com.cruise.activity.entity.PassengerLocationLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

@Component
public class TelemetryBuffer {
    public record Sample(Long beaconId, PassengerLocationLog location) {}
    private final ArrayBlockingQueue<Sample> samples;
    public TelemetryBuffer(@Value("${cruise.safety.telemetry.buffer-capacity:20000}") int capacity) {
        samples = new ArrayBlockingQueue<>(capacity);
    }
    public void offer(Sample sample) {
        if (!samples.offer(sample)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
            "Telemetry buffer full; retry this ping later");
    }
    public List<Sample> drain(int maximum) {
        List<Sample> result = new ArrayList<>(); samples.drainTo(result, maximum); return result;
    }
    public int size() { return samples.size(); }
}
