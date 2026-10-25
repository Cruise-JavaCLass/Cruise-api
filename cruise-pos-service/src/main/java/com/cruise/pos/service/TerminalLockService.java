package com.cruise.pos.service;

import com.cruise.pos.dto.TerminalLockCommand;
import com.cruise.pos.dto.TerminalLockStatusResponse;
import com.cruise.pos.entity.PosAuditLog;
import com.cruise.pos.enums.TerminalLockStatus;
import com.cruise.pos.event.TerminalLockEvent;
import com.cruise.pos.repository.PosAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TerminalLockService {

    private final ApplicationEventPublisher eventPublisher;
    private final PosAuditLogRepository auditLogRepository;

    private final ConcurrentHashMap<String, TerminalLockStatusResponse> terminalStates =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> terminalLocks =
            new ConcurrentHashMap<>();

    @Transactional
    public TerminalLockStatusResponse lockTerminal(TerminalLockCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("TerminalLockCommand must not be null");
        }

        if (cmd.terminalId() == null || cmd.terminalId().isBlank()) {
            throw new IllegalArgumentException("terminalId must not be blank");
        }
        if (cmd.command() == null) {
            throw new IllegalArgumentException("command must not be null");
        }

        Object terminalLock = terminalLocks.computeIfAbsent(cmd.terminalId(), k -> new Object());
        synchronized (terminalLock) {
            TerminalLockStatus newStatus = resolveStatus(cmd.command());
            Instant now = Instant.now();

            TerminalLockStatusResponse response = new TerminalLockStatusResponse(
                    cmd.terminalId(),
                    newStatus,
                    cmd.issuedBy(),
                    now,
                    cmd.justification()
            );

            String details = cmd.terminalId();
            if (cmd.justification() != null && !cmd.justification().isBlank()) {
                String trimmedReason = cmd.justification().trim();
                if (trimmedReason.length() > 200) {
                    trimmedReason = trimmedReason.substring(0, 200);
                }
                details = details + " | Reason: " + trimmedReason;
            }

            PosAuditLog auditLog = PosAuditLog.builder()
                    .action("TERMINAL_" + cmd.command().toUpperCase())
                    .performedBy(cmd.issuedBy() != null ? cmd.issuedBy() : "SYSTEM")
                    .details(details)
                    .build();
            auditLogRepository.save(auditLog);

            terminalStates.put(cmd.terminalId(), response);

            eventPublisher.publishEvent(new TerminalLockEvent(
                    this,
                    cmd.terminalId(),
                    cmd.command(),
                    cmd.issuedBy(),
                    cmd.justification(),
                    now
            ));

            log.info("Terminal lock action: terminalId={}, command={}, issuedBy={}",
                    cmd.terminalId(), cmd.command(), cmd.issuedBy());

            return response;
        }
    }

    @Transactional(readOnly = true)
    public TerminalLockStatusResponse getTerminalStatus(String terminalId) {
        return terminalStates.getOrDefault(terminalId,
                new TerminalLockStatusResponse(terminalId, TerminalLockStatus.UNLOCKED, null, null, null));
    }

    @Transactional(readOnly = true)
    public List<TerminalLockStatusResponse> getAllTerminalStatuses() {
        return List.copyOf(terminalStates.values());
    }

    private TerminalLockStatus resolveStatus(String command) {
        return switch (command.toUpperCase()) {
            case "LOCK" -> TerminalLockStatus.LOCKED;
            case "UNLOCK" -> TerminalLockStatus.UNLOCKED;
            case "FORCE_UNLOCK" -> TerminalLockStatus.FORCE_UNLOCKED;
            default -> throw new IllegalArgumentException("Unknown lock command: " + command);
        };
    }
}
