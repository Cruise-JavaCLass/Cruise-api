package com.cruise.pos.service;

import com.cruise.pos.dto.ManagerOverrideRequest;
import com.cruise.pos.dto.ManagerOverrideResponse;
import com.cruise.pos.entity.PosAuditLog;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * KAN-608 — Unit tests for supervisor override rules and audit dispatch.
 * Verifies that ManagerOverrideService correctly validates credentials,
 * dispatches audit events, and returns proper responses.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ManagerOverrideService — supervisor override rule tests")
class ManagerOverrideServiceTest {

    @Mock
    private PosManagerAuthService posManagerAuthService;

    @Mock
    private PosAuditLogRepository auditLogRepository;

    @InjectMocks
    private ManagerOverrideService managerOverrideService;

    private ManagerOverrideRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new ManagerOverrideRequest(
                "MGR-01",
                "8888",
                "PROCESS_REFUND",
                null,
                "Customer requested refund for damaged goods"
        );
    }

    @Nested
    @DisplayName("Approved override scenarios")
    class ApprovedOverride {

        @Test
        @DisplayName("returns approved=true and saves audit log when credentials are valid")
        void approvedOverride_savesAuditLogAndReturnsApproved() {
            when(posManagerAuthService.verifyRefundApproval("MGR-01", "8888")).thenReturn(true);
            when(auditLogRepository.save(any(PosAuditLog.class))).thenAnswer(inv -> {
                PosAuditLog log = inv.getArgument(0);
                // simulate DB-assigned ID
                return log;
            });

            ManagerOverrideResponse response = managerOverrideService.dispatchOverride(validRequest, "SERVER-BOB");

            assertThat(response.approved()).isTrue();
            assertThat(response.managerId()).isEqualTo("MGR-01");
            assertThat(response.action()).isEqualTo("PROCESS_REFUND");
            assertThat(response.timestamp()).isNotNull();

            ArgumentCaptor<PosAuditLog> captor = ArgumentCaptor.forClass(PosAuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            PosAuditLog savedLog = captor.getValue();
            assertThat(savedLog.getAction()).isEqualTo("MANAGER_OVERRIDE");
            assertThat(savedLog.getPerformedBy()).isEqualTo("SERVER-BOB");
            assertThat(savedLog.getManagerId()).isEqualTo("MGR-01");
            assertThat(savedLog.getReason()).contains("PROCESS_REFUND");
        }

        @Test
        @DisplayName("normalizes managerId by trimming and uppercasing before verification and audit")
        void approvedOverride_normalizesManagerId() {
            ManagerOverrideRequest unnormalizedReq = new ManagerOverrideRequest(
                    "  mgr-01  ",
                    "8888",
                    "PROCESS_REFUND",
                    null,
                    "Customer requested refund"
            );
            when(posManagerAuthService.verifyRefundApproval("MGR-01", "8888")).thenReturn(true);
            when(auditLogRepository.save(any(PosAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

            ManagerOverrideResponse response = managerOverrideService.dispatchOverride(unnormalizedReq, "SERVER-BOB");

            assertThat(response.approved()).isTrue();
            assertThat(response.managerId()).isEqualTo("MGR-01");

            ArgumentCaptor<PosAuditLog> captor = ArgumentCaptor.forClass(PosAuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            assertThat(captor.getValue().getManagerId()).isEqualTo("MGR-01");
        }

        @Test
        @DisplayName("audit log details include the justification text")
        void approvedOverride_includesJustificationInAuditDetails() {
            when(posManagerAuthService.verifyRefundApproval(any(), any())).thenReturn(true);
            when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            managerOverrideService.dispatchOverride(validRequest, "SERVER-ALICE");

            ArgumentCaptor<PosAuditLog> captor = ArgumentCaptor.forClass(PosAuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            assertThat(captor.getValue().getDetails()).contains("damaged goods");
        }
    }

    @Nested
    @DisplayName("Rejected override scenarios")
    class RejectedOverride {

        @Test
        @DisplayName("returns approved=false and does NOT save audit log when credentials are invalid")
        void rejectedOverride_doesNotSaveAuditLog() {
            when(posManagerAuthService.verifyRefundApproval("MGR-01", "8888")).thenReturn(false);

            ManagerOverrideResponse response = managerOverrideService.dispatchOverride(validRequest, "SERVER-BOB");

            assertThat(response.approved()).isFalse();
            assertThat(response.managerId()).isEqualTo("MGR-01");
            verifyNoInteractions(auditLogRepository);
        }

        @Test
        @DisplayName("returns approved=false for unknown manager ID")
        void rejectedOverride_unknownManager() {
            ManagerOverrideRequest unknownMgrReq = new ManagerOverrideRequest(
                    "UNKNOWN-MGR", "1234", "VOID_TRANSACTION", null, "test justification"
            );
            when(posManagerAuthService.verifyRefundApproval("UNKNOWN-MGR", "1234")).thenReturn(false);

            ManagerOverrideResponse response = managerOverrideService.dispatchOverride(unknownMgrReq, "SERVER-ALICE");

            assertThat(response.approved()).isFalse();
            verifyNoInteractions(auditLogRepository);
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("throws IllegalArgumentException when request is null")
        void nullRequest_throwsException() {
            assertThatThrownBy(() -> managerOverrideService.dispatchOverride(null, "SERVER-BOB"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("uses 'ANONYMOUS' as performedBy when null is passed")
        void nullPerformedBy_usesAnonymous() {
            when(posManagerAuthService.verifyRefundApproval(any(), any())).thenReturn(true);
            when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            managerOverrideService.dispatchOverride(validRequest, null);

            ArgumentCaptor<PosAuditLog> captor = ArgumentCaptor.forClass(PosAuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            assertThat(captor.getValue().getPerformedBy()).isEqualTo("ANONYMOUS");
        }
    }
}
