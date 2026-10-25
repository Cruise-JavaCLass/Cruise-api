package com.cruise.pos.security;

import java.util.Set;

/**
 * The three concrete POS roles used in on-board operations.
 * Each role carries an immutable set of {@link PosPermission}s.
 */
public enum PosRole {

    SERVER(Set.of(
            PosPermission.PROCESS_SALE,
            PosPermission.VIEW_FOLIO,
            PosPermission.VIEW_SHIFT_REPORT
    )),

    BARTENDER(Set.of(
            PosPermission.PROCESS_SALE,
            PosPermission.VIEW_FOLIO,
            PosPermission.VIEW_SHIFT_REPORT
    )),

    POS_SUPERVISOR(Set.of(
            PosPermission.PROCESS_SALE,
            PosPermission.PROCESS_REFUND,
            PosPermission.VOID_TRANSACTION,
            PosPermission.VIEW_SHIFT_REPORT,
            PosPermission.CLOSE_SHIFT,
            PosPermission.MANAGE_CATALOG,
            PosPermission.VIEW_FOLIO,
            PosPermission.APPLY_DISCOUNT
    ));

    private final Set<PosPermission> permissions;

    PosRole(Set<PosPermission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<PosPermission> getPermissions() {
        return permissions;
    }
}
