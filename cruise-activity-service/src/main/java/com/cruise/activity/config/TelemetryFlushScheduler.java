package com.cruise.activity.config;

import com.cruise.activity.service.TelemetryBatchFlusher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="cruise.safety.telemetry.scheduling-enabled",havingValue="true",matchIfMissing=true)
public class TelemetryFlushScheduler {
    private final TelemetryBatchFlusher flusher;
    @Scheduled(fixedDelayString="${cruise.safety.telemetry.flush-delay-ms:1000}")
    public void tick() { if (flusher.reserve()) flusher.flushAsync(); }
}
