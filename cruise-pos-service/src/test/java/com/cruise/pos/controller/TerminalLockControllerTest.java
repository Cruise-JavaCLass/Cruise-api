package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.TerminalLockActionRequest;
import com.cruise.pos.dto.TerminalLockCommand;
import com.cruise.pos.dto.TerminalLockStatusResponse;
import com.cruise.pos.enums.TerminalLockStatus;
import com.cruise.pos.service.TerminalLockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TerminalLockController Unit Tests")
class TerminalLockControllerTest {

    @Mock
    private TerminalLockService terminalLockService;

    @InjectMocks
    private TerminalLockController controller;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private final TerminalLockStatusResponse sampleStatus = new TerminalLockStatusResponse(
            "TERM-01",
            TerminalLockStatus.LOCKED,
            "SYSTEM",
            Instant.now(),
            "Emergency lock"
    );

    @Test
    @DisplayName("lockTerminal: locks terminal and returns response")
    void testLockTerminal() {
        TerminalLockActionRequest req = new TerminalLockActionRequest("Emergency lock");
        when(terminalLockService.lockTerminal(any(TerminalLockCommand.class))).thenReturn(sampleStatus);

        ApiResponse<TerminalLockStatusResponse> response = controller.lockTerminal("TERM-01", req);

        assertThat(response).isNotNull();
        assertThat(response.getData().terminalId()).isEqualTo("TERM-01");
        assertThat(response.getData().status()).isEqualTo(TerminalLockStatus.LOCKED);
        verify(terminalLockService).lockTerminal(any(TerminalLockCommand.class));
    }

    @Test
    @DisplayName("unlockTerminal: unlocks terminal and returns response")
    void testUnlockTerminal() {
        TerminalLockStatusResponse unlocked = new TerminalLockStatusResponse(
                "TERM-01", TerminalLockStatus.UNLOCKED, "SYSTEM", Instant.now(), "Normal operation"
        );
        when(terminalLockService.lockTerminal(any(TerminalLockCommand.class))).thenReturn(unlocked);

        ApiResponse<TerminalLockStatusResponse> response = controller.unlockTerminal("TERM-01", null);

        assertThat(response).isNotNull();
        assertThat(response.getData().status()).isEqualTo(TerminalLockStatus.UNLOCKED);
    }

    @Test
    @DisplayName("forceUnlockTerminal: force unlocks terminal")
    void testForceUnlockTerminal() {
        TerminalLockStatusResponse forceUnlocked = new TerminalLockStatusResponse(
                "TERM-01", TerminalLockStatus.UNLOCKED, "SYSTEM", Instant.now(), "Manager override"
        );
        when(terminalLockService.lockTerminal(any(TerminalLockCommand.class))).thenReturn(forceUnlocked);

        ApiResponse<TerminalLockStatusResponse> response = controller.forceUnlockTerminal("TERM-01", new TerminalLockActionRequest("Manager override"));

        assertThat(response).isNotNull();
        assertThat(response.getData().status()).isEqualTo(TerminalLockStatus.UNLOCKED);
    }

    @Test
    @DisplayName("getTerminalStatus: returns status for single terminal")
    void testGetTerminalStatus() {
        when(terminalLockService.getTerminalStatus("TERM-01")).thenReturn(sampleStatus);

        ApiResponse<TerminalLockStatusResponse> response = controller.getTerminalStatus("TERM-01");

        assertThat(response).isNotNull();
        assertThat(response.getData().terminalId()).isEqualTo("TERM-01");
        verify(terminalLockService).getTerminalStatus("TERM-01");
    }

    @Test
    @DisplayName("getAllTerminalStatuses: returns list of all terminal lock statuses")
    void testGetAllTerminalStatuses() {
        when(terminalLockService.getAllTerminalStatuses()).thenReturn(List.of(sampleStatus));

        ApiResponse<List<TerminalLockStatusResponse>> response = controller.getAllTerminalStatuses();

        assertThat(response).isNotNull();
        assertThat(response.getData()).hasSize(1);
        verify(terminalLockService).getAllTerminalStatuses();
    }
}
