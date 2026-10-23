package com.cruise.pos.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;

public class TerminalLockEvent extends ApplicationEvent {

    private final String terminalId;
    private final String command;
    private final String issuedBy;
    private final String justification;
    private final Instant occurredAt; // renamed from 'timestamp' to avoid clash with ApplicationEvent.getTimestamp()

    public TerminalLockEvent(Object source, String terminalId, String command,
                             String issuedBy, String justification, Instant occurredAt) {
        super(source);
        this.terminalId = terminalId;
        this.command = command;
        this.issuedBy = issuedBy;
        this.justification = justification;
        this.occurredAt = occurredAt;
    }

    public String getTerminalId() {
        return terminalId;
    }

    public String getCommand() {
        return command;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public String getJustification() {
        return justification;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
