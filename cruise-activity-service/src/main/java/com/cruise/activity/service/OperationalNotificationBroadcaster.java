package com.cruise.activity.service;

import com.cruise.activity.dto.OperationalNotificationEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class OperationalNotificationBroadcaster {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        emitter.onError((e) -> {
            emitters.remove(emitter);
        });

        // Send handshake
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data("Connected to Shipboard Operational Alert Gateway"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void broadcast(OperationalNotificationEventDto event) {
        log.info("Broadcasting operational alert to {} SSE subscribers: category={}, title={}",
                emitters.size(), event.getCategory(), event.getTitle());

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("OPERATIONAL_ALERT")
                        .data(event));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }
}
