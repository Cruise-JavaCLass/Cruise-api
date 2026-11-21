package com.cruise.activity.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service @RequiredArgsConstructor @Slf4j
public class TelemetryBatchFlusher {
    private final TelemetryBuffer buffer;
    private final TelemetryBatchWriter writer;
    private final AtomicBoolean reserved = new AtomicBoolean();
    private List<TelemetryBuffer.Sample> pending = List.of();
    public boolean reserve() { return reserved.compareAndSet(false,true); }
    @Async("safetyTelemetryExecutor")
    public void flushAsync() {
        try { flushPending(); }
        catch (RuntimeException error) { log.warn("Telemetry batch retained for retry",error); }
        finally { reserved.set(false); }
    }
    public synchronized void flushPending() {
        if (pending.isEmpty()) pending=buffer.drain(1000);
        if (!pending.isEmpty()) {
            writer.write(pending);
            pending=List.of(); // Retain the complete batch when the transaction fails.
        }
    }
    @PreDestroy
    public synchronized void flushOnShutdown() {
        try { while (!pending.isEmpty() || buffer.size()>0) flushPending(); }
        catch (RuntimeException error) { log.error("Unable to flush telemetry before shutdown",error); }
    }
}
