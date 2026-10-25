package com.cruise.pos.service;

import com.cruise.pos.dto.TerminalLockCommand;
import com.cruise.pos.dto.TerminalLockStatusResponse;
import com.cruise.pos.entity.PosAuditLog;
import com.cruise.pos.enums.TerminalLockStatus;
import com.cruise.pos.repository.PosAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * KAN-608 — Unit tests for remote terminal lock/unlock service.
 * Verifies state transitions, event publishing, and audit trail.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TerminalLockService — remote lock/unlock tests")
class TerminalLockServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PosAuditLogRepository auditLogRepository;

    @InjectMocks
    private TerminalLockService terminalLockService;

    private static final String TERMINAL_ID = "POS-DECK9-01";

    @BeforeEach
    void setUp() {
        lenient().when(auditLogRepository.save(any(PosAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("LOCK command")
    class LockCommand {

        @Test
        @DisplayName("locks terminal and returns LOCKED status")
        void lockTerminal_returnsLockedStatus() {
            TerminalLockCommand cmd = new TerminalLockCommand(TERMINAL_ID, "LOCK", "OPS-MGR-01", "Routine security lock");

            TerminalLockStatusResponse response = terminalLockService.lockTerminal(cmd);

            assertThat(response.terminalId()).isEqualTo(TERMINAL_ID);
            assertThat(response.status()).isEqualTo(TerminalLockStatus.LOCKED);
            assertThat(response.lockedBy()).isEqualTo("OPS-MGR-01");
            assertThat(response.lockedAt()).isNotNull();
        }

        @Test
        @DisplayName("publishes TerminalLockEvent after LOCK command")
        void lockTerminal_publishesEvent() {
            TerminalLockCommand cmd = new TerminalLockCommand(TERMINAL_ID, "LOCK", "OPS-MGR-01", "Security lock");

            terminalLockService.lockTerminal(cmd);

            verify(eventPublisher).publishEvent(any());
        }

        @Test
        @DisplayName("writes TERMINAL_LOCK audit log entry")
        void lockTerminal_writesAuditLog() {
            TerminalLockCommand cmd = new TerminalLockCommand(TERMINAL_ID, "LOCK", "OPS-MGR-01", "Security lock");

            terminalLockService.lockTerminal(cmd);

            ArgumentCaptor<PosAuditLog> captor = ArgumentCaptor.forClass(PosAuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            PosAuditLog log = captor.getValue();
            assertThat(log.getAction()).isEqualTo("TERMINAL_LOCK");
            assertThat(log.getPerformedBy()).isEqualTo("OPS-MGR-01");
            assertThat(log.getDetails()).contains(TERMINAL_ID);
        }
    }

    @Nested
    @DisplayName("UNLOCK command")
    class UnlockCommand {

        @Test
        @DisplayName("unlocks terminal and returns UNLOCKED status")
        void unlockTerminal_returnsUnlockedStatus() {
            // First lock
            terminalLockService.lockTerminal(new TerminalLockCommand(TERMINAL_ID, "LOCK", "OPS-MGR-01", "lock"));
            // Then unlock
            TerminalLockCommand unlockCmd = new TerminalLockCommand(TERMINAL_ID, "UNLOCK", "OPS-MGR-01", "Shift started");
            TerminalLockStatusResponse response = terminalLockService.lockTerminal(unlockCmd);

            assertThat(response.status()).isEqualTo(TerminalLockStatus.UNLOCKED);
        }
    }

    @Nested
    @DisplayName("FORCE_UNLOCK command")
    class ForceUnlockCommand {

        @Test
        @DisplayName("force unlocks terminal and returns FORCE_UNLOCKED status")
        void forceUnlock_returnsForceUnlockedStatus() {
            TerminalLockCommand cmd = new TerminalLockCommand(TERMINAL_ID, "FORCE_UNLOCK", "OPS-MGR-01", "Terminal unresponsive");

            TerminalLockStatusResponse response = terminalLockService.lockTerminal(cmd);

            assertThat(response.status()).isEqualTo(TerminalLockStatus.FORCE_UNLOCKED);
        }

        @Test
        @DisplayName("FORCE_UNLOCK writes audit log with TERMINAL_FORCE_UNLOCK action")
        void forceUnlock_writesAuditLog() {
            TerminalLockCommand cmd = new TerminalLockCommand(TERMINAL_ID, "FORCE_UNLOCK", "OPS-MGR-01", "Emergency");

            terminalLockService.lockTerminal(cmd);

            ArgumentCaptor<PosAuditLog> captor = ArgumentCaptor.forClass(PosAuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            assertThat(captor.getValue().getAction()).isEqualTo("TERMINAL_FORCE_UNLOCK");
            assertThat(captor.getValue().getDetails()).contains(TERMINAL_ID).contains("Reason: Emergency");
        }

        @Test
        @DisplayName("if auditLogRepository.save throws exception, in-memory terminal state is not updated")
        void lockTerminal_auditFails_stateNotUpdated() {
            reset(auditLogRepository);
            when(auditLogRepository.save(any(PosAuditLog.class))).thenThrow(new RuntimeException("DB error"));

            TerminalLockCommand cmd = new TerminalLockCommand("FAIL-TERM", "LOCK", "MGR", "test fail");

            assertThatThrownBy(() -> terminalLockService.lockTerminal(cmd))
                    .isInstanceOf(RuntimeException.class);

            TerminalLockStatusResponse status = terminalLockService.getTerminalStatus("FAIL-TERM");
            assertThat(status.status()).isEqualTo(TerminalLockStatus.UNLOCKED);
        }
    }

    @Nested
    @DisplayName("Terminal status queries")
    class StatusQueries {

        @Test
        @DisplayName("getTerminalStatus returns UNLOCKED for unknown terminal (default)")
        void getStatus_unknownTerminal_defaultsToUnlocked() {
            TerminalLockStatusResponse status = terminalLockService.getTerminalStatus("UNKNOWN-TERMINAL");
            assertThat(status.status()).isEqualTo(TerminalLockStatus.UNLOCKED);
        }

        @Test
        @DisplayName("getTerminalStatus returns current state after lock")
        void getStatus_afterLock_returnsLocked() {
            terminalLockService.lockTerminal(new TerminalLockCommand(TERMINAL_ID, "LOCK", "OPS-MGR-01", "test"));

            TerminalLockStatusResponse status = terminalLockService.getTerminalStatus(TERMINAL_ID);
            assertThat(status.status()).isEqualTo(TerminalLockStatus.LOCKED);
        }

        @Test
        @DisplayName("getAllTerminalStatuses returns empty list initially")
        void getAllStatuses_returnsEmptyInitially() {
            List<TerminalLockStatusResponse> all = terminalLockService.getAllTerminalStatuses();
            assertThat(all).isEmpty();
        }

        @Test
        @DisplayName("getAllTerminalStatuses includes terminal after lock")
        void getAllStatuses_includesTerminalAfterLock() {
            terminalLockService.lockTerminal(new TerminalLockCommand(TERMINAL_ID, "LOCK", "OPS-MGR-01", "test"));

            List<TerminalLockStatusResponse> all = terminalLockService.getAllTerminalStatuses();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).terminalId()).isEqualTo(TERMINAL_ID);
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("throws exception when command is null")
        void nullCommand_throwsException() {
            assertThatThrownBy(() -> terminalLockService.lockTerminal(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws exception when terminalId is blank")
        void blankTerminalId_throwsException() {
            TerminalLockCommand cmd = new TerminalLockCommand("", "LOCK", "OPS-MGR-01", "test");
            assertThatThrownBy(() -> terminalLockService.lockTerminal(cmd))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
