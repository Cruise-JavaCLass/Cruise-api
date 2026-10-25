package com.cruise.pos.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TerminalLockEventListener {

    @EventListener
    public void logBroadcast(TerminalLockEvent event) {
        log.info("Broadcasting terminal {} {} command to FCM/MQTT channel [issuedBy={}, timestamp={}]",
                event.getTerminalId(), event.getCommand(), event.getIssuedBy(), event.getTimestamp());

        // Simulate FCM push notification
        log.info("[FCM/MQTT SIMULATE] Push notification dispatched: terminalId={}, command={}, justification={}",
                event.getTerminalId(), event.getCommand(), event.getJustification());
    }
}
