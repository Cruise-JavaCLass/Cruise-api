package com.cruise.common.security;

import java.util.Set;
import static com.cruise.common.security.Permission.*;
import static com.cruise.common.security.AccessRequirement.*;

/** Ordered domain rules: specific aliases precede broader resource families; unknown APIs deny. */
public final class ServiceAccessPolicy implements AccessPolicy {
    private static final Set<String> HEALTH = Set.of("/api/ping", "/api/info", "/api/health",
            "/api/auth/health", "/api/itinerary/health", "/api/activities/health", "/api/pos/health");
    private static final Set<String> PUBLIC_AUTH = Set.of("/api/auth/login", "/api/auth/refresh",
            "/api/auth/refresh-token", "/api/auth/verify-token", "/api/auth/validate-token",
            "/api/auth/access-context", "/api/auth/logout", "/api/auth/verify-password",
            "/api/auth/revoke-token", "/api/auth/token/revoke");
    private static final Set<String> SESSION = Set.of("/api/auth/me", "/api/auth/logout",
            "/api/auth/change-password", "/api/auth/verify-password", "/api/auth/token/pair",
            "/api/auth/revoke-token", "/api/auth/token/revoke", "/api/auth/permissions");

    @Override
    public AccessRequirement requirement(String method, String path) {
        boolean read = "GET".equals(method) || "HEAD".equals(method);
        if (read && HEALTH.contains(path)) return publicAccess();
        // These endpoints prove credentials/token in their payload, and retain their existing 405 contract.
        if (PUBLIC_AUTH.contains(path)) return publicAccess();
        if (SESSION.contains(path)) return authenticated();
        if (under(path, "/api/auth/users") || under(path, "/api/auth/roles")) return require(USERS_MANAGE);
        if (under(path, "/api/admin/audit")) return require(SECURITY_AUDIT_READ);
        if (under(path, "/api/admin/pos/terminals")) return require(TERMINALS_MANAGE);
        if (under(path, "/api/admin/voyage-lifecycle") || under(path, "/api/admin/voyages")) {
            return require(OPERATIONS_MANAGE);
        }
        if (under(path, "/api/itinerary") || under(path, "/api/admin/cruise-area-policy")) {
            return require(read ? ITINERARY_READ : ITINERARY_MANAGE);
        }
        if (under(path, "/api/passenger-portal/catalog")) return read ? require(CATALOG_READ) : deny();
        if (under(path, "/api/v1/tour-providers") && path.contains("/settlement")) return require(FINANCE_MANAGE);
        if (under(path, "/api/excursions") || under(path, "/api/activities/excursions")
                || under(path, "/api/v1/shore-excursions") || under(path, "/api/v1/tour-providers")) {
            return require(read ? EXCURSIONS_READ : EXCURSIONS_MANAGE);
        }
        if (under(path, "/api/passengers") || under(path, "/api/activities/passengers")) {
            if (path.contains("/folio")) return require(read ? POS_READ : FINANCE_MANAGE);
            return require(read ? PASSENGERS_READ : PASSENGERS_MANAGE);
        }
        if (under(path, "/api/activities")) return require(read ? ACTIVITIES_READ : ACTIVITIES_MANAGE);
        if (under(path, "/api/operations") || under(path, "/api/feedback/reviews")) return require(OPERATIONS_MANAGE);
        if (under(path, "/api/service-recovery")) return require(FINANCE_MANAGE);
        if (under(path, "/api/pos") || under(path, "/api/v1/pos")) {
            String posPath = path.replaceFirst("^/api/v1/pos", "/api/pos");
            if (under(posPath, "/api/pos/reports")) return require(REPORTS_READ);
            if (under(posPath, "/api/pos/auth/manifest")) return require(POS_OPERATE);
            if (!read && posPath.matches("/api/pos/transactions/[^/]+/(refund|void)")) return require(FINANCE_MANAGE);
            if (under(posPath, "/api/pos/terminals") && !read) return require(OPERATIONS_MANAGE);
            if (under(posPath, "/api/pos/folios") || under(posPath, "/api/pos/disputes")
                    || under(posPath, "/api/pos/cabin-billing") || under(posPath, "/api/pos/invoices")
                    || under(posPath, "/api/pos/settlements") || under(posPath, "/api/pos/reconciliation")) {
                // Cashiers may post a charge; credit limits, settlement and dispute resolution require finance.
                if ("POST".equals(method) && posPath.matches("/api/pos/folios/[^/]+/ledger")) return require(POS_OPERATE);
                return require(read ? POS_READ : FINANCE_MANAGE);
            }
            if (!read && (under(posPath, "/api/pos/products") || under(posPath, "/api/pos/catalog")
                    || under(posPath, "/api/pos/pricing/happy-hour-rules"))) return require(OPERATIONS_MANAGE);
            return require(read ? POS_READ : POS_OPERATE);
        }
        return deny();
    }

    private static boolean under(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }
}
