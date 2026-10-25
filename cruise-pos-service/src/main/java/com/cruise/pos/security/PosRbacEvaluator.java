package com.cruise.pos.security;

import com.cruise.pos.service.PosManagerAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * RBAC policy evaluator for POS operations (KAN-605).
 *
 * <p>Role mapping strategy:
 * <ul>
 *   <li>{@code POS_STAFF}         → SERVER base permissions</li>
 *   <li>{@code OPERATIONS_MANAGER}→ full POS permissions including FORCE_UNLOCK_TERMINAL</li>
 *   <li>{@code SYSTEM_ADMIN}      → full permissions (super-set of OPERATIONS_MANAGER)</li>
 *   <li>All other roles           → no POS permissions</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosRbacEvaluator {

    /** Permissions granted to OPERATIONS_MANAGER in POS context. */
    private static final Set<PosPermission> OPERATIONS_MANAGER_PERMISSIONS = Set.of(
            PosPermission.PROCESS_SALE,
            PosPermission.PROCESS_REFUND,
            PosPermission.VOID_TRANSACTION,
            PosPermission.VIEW_SHIFT_REPORT,
            PosPermission.CLOSE_SHIFT,
            PosPermission.MANAGE_CATALOG,
            PosPermission.VIEW_FOLIO,
            PosPermission.APPLY_DISCOUNT,
            PosPermission.FORCE_UNLOCK_TERMINAL
    );

    private final PosManagerAuthService posManagerAuthService;

    /**
     * Evaluates whether the given system role has the requested POS permission.
     *
     * @param role       the raw role string from JWT (e.g. "POS_STAFF", "OPERATIONS_MANAGER")
     * @param permission the permission to check
     * @return {@code true} if the role carries the permission
     */
    public boolean hasPermission(String role, PosPermission permission) {
        if (role == null || permission == null) {
            return false;
        }

        return switch (role.toUpperCase()) {
            case "SYSTEM_ADMIN"        -> true;
            case "OPERATIONS_MANAGER"  -> OPERATIONS_MANAGER_PERMISSIONS.contains(permission);
            case "POS_STAFF"           -> PosRole.SERVER.getPermissions().contains(permission);
            default -> {
                log.debug("Role '{}' has no POS permissions", role);
                yield false;
            }
        };
    }

    /**
     * Evaluates permission with optional manager override.
     *
     * <p>If the base role already has the permission, the manager credentials are not checked.
     * Otherwise, the manager PIN is verified via {@link PosManagerAuthService}; if valid, the
     * action is permitted (manager override path).
     *
     * @param role        the raw role string from JWT
     * @param permission  the permission to check
     * @param managerId   manager identifier presented for override
     * @param managerPin  manager PIN (plain-text, hashed internally by PosManagerAuthService)
     * @return {@code true} if the role has the permission directly or via manager override
     */
    public boolean hasPermissionWithOverride(String role,
                                             PosPermission permission,
                                             String managerId,
                                             String managerPin) {
        if (hasPermission(role, permission)) {
            return true;
        }

        if (role == null || permission == null
                || !"POS_STAFF".equalsIgnoreCase(role)
                || (permission != PosPermission.PROCESS_REFUND
                && permission != PosPermission.VOID_TRANSACTION
                && permission != PosPermission.APPLY_DISCOUNT)) {
            return false;
        }

        boolean overrideGranted = posManagerAuthService.verifyRefundApproval(managerId, managerPin);

        if (overrideGranted) {
            log.info("Manager override granted: managerId={}, permission={}, requestingRole={}",
                    managerId, permission, role);
        } else {
            log.warn("Manager override denied: managerId={}, permission={}, requestingRole={}",
                    managerId, permission, role);
        }

        return overrideGranted;
    }
}
