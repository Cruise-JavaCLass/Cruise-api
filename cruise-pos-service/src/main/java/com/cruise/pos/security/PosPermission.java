package com.cruise.pos.security;

/**
 * Granular permissions for POS operations.
 * Used by {@link PosRbacEvaluator} to enforce role-based access control.
 */
public enum PosPermission {
    PROCESS_SALE,            // SERVER, BARTENDER, POS_SUPERVISOR
    PROCESS_REFUND,          // POS_SUPERVISOR only (or with manager override)
    VOID_TRANSACTION,        // POS_SUPERVISOR only (or with manager override)
    VIEW_SHIFT_REPORT,       // All POS roles
    CLOSE_SHIFT,             // POS_SUPERVISOR only
    MANAGE_CATALOG,          // POS_SUPERVISOR + OPERATIONS_MANAGER
    VIEW_FOLIO,              // All POS roles
    APPLY_DISCOUNT,          // POS_SUPERVISOR only (or manager override)
    FORCE_UNLOCK_TERMINAL    // OPERATIONS_MANAGER only
}
