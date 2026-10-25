package com.cruise.pos.security;

import com.cruise.pos.service.PosManagerAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KAN-608 — Unit tests for RBAC permission evaluator.
 * Verifies permission matrix for POS_STAFF, OPERATIONS_MANAGER, and SYSTEM_ADMIN roles.
 */
@DisplayName("PosRbacEvaluator — permission matrix tests")
class PosRbacEvaluatorTest {

    private PosRbacEvaluator evaluator;
    private PosManagerAuthService authService;

    @BeforeEach
    void setUp() {
        authService = new PosManagerAuthService();
        evaluator = new PosRbacEvaluator(authService);
    }

    @Nested
    @DisplayName("POS_STAFF (base server) permissions")
    class PosStaffPermissions {

        @Test
        @DisplayName("PROCESS_SALE is granted to POS_STAFF")
        void posStaff_canProcessSale() {
            assertThat(evaluator.hasPermission("POS_STAFF", PosPermission.PROCESS_SALE)).isTrue();
        }

        @Test
        @DisplayName("VIEW_FOLIO is granted to POS_STAFF")
        void posStaff_canViewFolio() {
            assertThat(evaluator.hasPermission("POS_STAFF", PosPermission.VIEW_FOLIO)).isTrue();
        }

        @Test
        @DisplayName("VIEW_SHIFT_REPORT is granted to POS_STAFF")
        void posStaff_canViewShiftReport() {
            assertThat(evaluator.hasPermission("POS_STAFF", PosPermission.VIEW_SHIFT_REPORT)).isTrue();
        }

        @Test
        @DisplayName("PROCESS_REFUND is denied to POS_STAFF")
        void posStaff_cannotProcessRefund() {
            assertThat(evaluator.hasPermission("POS_STAFF", PosPermission.PROCESS_REFUND)).isFalse();
        }

        @Test
        @DisplayName("VOID_TRANSACTION is denied to POS_STAFF")
        void posStaff_cannotVoidTransaction() {
            assertThat(evaluator.hasPermission("POS_STAFF", PosPermission.VOID_TRANSACTION)).isFalse();
        }

        @Test
        @DisplayName("CLOSE_SHIFT is denied to POS_STAFF")
        void posStaff_cannotCloseShift() {
            assertThat(evaluator.hasPermission("POS_STAFF", PosPermission.CLOSE_SHIFT)).isFalse();
        }

        @Test
        @DisplayName("FORCE_UNLOCK_TERMINAL is denied to POS_STAFF")
        void posStaff_cannotForceUnlockTerminal() {
            assertThat(evaluator.hasPermission("POS_STAFF", PosPermission.FORCE_UNLOCK_TERMINAL)).isFalse();
        }

        @Test
        @DisplayName("APPLY_DISCOUNT is denied to POS_STAFF")
        void posStaff_cannotApplyDiscount() {
            assertThat(evaluator.hasPermission("POS_STAFF", PosPermission.APPLY_DISCOUNT)).isFalse();
        }
    }

    @Nested
    @DisplayName("OPERATIONS_MANAGER permissions")
    class OperationsManagerPermissions {

        @ParameterizedTest
        @EnumSource(PosPermission.class)
        @DisplayName("OPERATIONS_MANAGER has all POS permissions including FORCE_UNLOCK_TERMINAL")
        void operationsManager_hasAllPermissions(PosPermission permission) {
            assertThat(evaluator.hasPermission("OPERATIONS_MANAGER", permission)).isTrue();
        }
    }

    @Nested
    @DisplayName("SYSTEM_ADMIN permissions")
    class SystemAdminPermissions {

        @ParameterizedTest
        @EnumSource(PosPermission.class)
        @DisplayName("SYSTEM_ADMIN has all POS permissions")
        void systemAdmin_hasAllPermissions(PosPermission permission) {
            assertThat(evaluator.hasPermission("SYSTEM_ADMIN", permission)).isTrue();
        }
    }

    @Nested
    @DisplayName("Unknown or unauthorized roles")
    class UnknownRolePermissions {

        @ParameterizedTest
        @EnumSource(PosPermission.class)
        @DisplayName("PASSENGER has no POS permissions")
        void passenger_hasNoPermissions(PosPermission permission) {
            assertThat(evaluator.hasPermission("PASSENGER", permission)).isFalse();
        }

        @Test
        @DisplayName("null role returns false for all permissions")
        void nullRole_returnsFalse() {
            assertThat(evaluator.hasPermission(null, PosPermission.PROCESS_SALE)).isFalse();
        }

        @Test
        @DisplayName("empty role returns false for all permissions")
        void emptyRole_returnsFalse() {
            assertThat(evaluator.hasPermission("", PosPermission.PROCESS_SALE)).isFalse();
        }
    }

    @Nested
    @DisplayName("Manager override grants elevated permission")
    class ManagerOverridePermissions {

        @Test
        @DisplayName("POS_STAFF can PROCESS_REFUND with valid manager override")
        void posStaff_canRefundWithManagerOverride() {
            assertThat(evaluator.hasPermissionWithOverride(
                    "POS_STAFF", PosPermission.PROCESS_REFUND, "MGR-01", "8888"
            )).isTrue();
        }

        @Test
        @DisplayName("POS_STAFF cannot PROCESS_REFUND with invalid manager PIN")
        void posStaff_cannotRefundWithWrongPin() {
            assertThat(evaluator.hasPermissionWithOverride(
                    "POS_STAFF", PosPermission.PROCESS_REFUND, "MGR-01", "wrong"
            )).isFalse();
        }

        @Test
        @DisplayName("POS_STAFF cannot PROCESS_REFUND with unknown manager ID")
        void posStaff_cannotRefundWithUnknownManager() {
            assertThat(evaluator.hasPermissionWithOverride(
                    "POS_STAFF", PosPermission.PROCESS_REFUND, "UNKNOWN-MGR", "8888"
            )).isFalse();
        }

        @Test
        @DisplayName("OPERATIONS_MANAGER does not need override — already has permission")
        void operationsManager_doesNotNeedOverrideForRefund() {
            // Direct permission check should be true without override
            assertThat(evaluator.hasPermission("OPERATIONS_MANAGER", PosPermission.PROCESS_REFUND)).isTrue();
        }

        @Test
        @DisplayName("Override with null managerId returns false")
        void override_withNullManagerId_returnsFalse() {
            assertThat(evaluator.hasPermissionWithOverride(
                    "POS_STAFF", PosPermission.VOID_TRANSACTION, null, "8888"
            )).isFalse();
        }

        @Test
        @DisplayName("Override with null pin returns false")
        void override_withNullPin_returnsFalse() {
            assertThat(evaluator.hasPermissionWithOverride(
                    "POS_STAFF", PosPermission.VOID_TRANSACTION, "MGR-01", null
            )).isFalse();
        }

        @Test
        @DisplayName("Override cannot grant FORCE_UNLOCK_TERMINAL to POS_STAFF")
        void posStaff_cannotOverrideForceUnlockTerminal() {
            assertThat(evaluator.hasPermissionWithOverride(
                    "POS_STAFF", PosPermission.FORCE_UNLOCK_TERMINAL, "MGR-01", "8888"
            )).isFalse();
        }

        @Test
        @DisplayName("Override cannot grant PROCESS_REFUND to PASSENGER")
        void passenger_cannotOverrideProcessRefund() {
            assertThat(evaluator.hasPermissionWithOverride(
                    "PASSENGER", PosPermission.PROCESS_REFUND, "MGR-01", "8888"
            )).isFalse();
        }
    }
}
