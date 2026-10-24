package com.cruise.pos.service;

import com.cruise.pos.dto.ManagerOverrideRequest;
import com.cruise.pos.dto.ManagerOverrideResponse;
import com.cruise.pos.entity.PosAuditLog;
import com.cruise.pos.repository.PosAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles manager override dispatch and audit trail creation (KAN-606).
 *
 * <p>Workflow:
 * <ol>
 *   <li>Validate the manager credentials via {@link PosManagerAuthService}.</li>
 *   <li>On approval: persist a {@link PosAuditLog} entry with action=MANAGER_OVERRIDE.</li>
 *   <li>Return a {@link ManagerOverrideResponse} with the outcome.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerOverrideService {

    private final PosManagerAuthService posManagerAuthService;
    private final PosAuditLogRepository posAuditLogRepository;

    /**
     * Dispatches a manager override request.
     *
     * @param req         the override request payload
     * @param performedBy username of the staff member requesting the override
     * @return the override outcome including audit log ID when approved
     */
    @Transactional
    public ManagerOverrideResponse dispatchOverride(ManagerOverrideRequest req, String performedBy) {
        if (req == null) {
            throw new IllegalArgumentException("ManagerOverrideRequest must not be null");
        }

        String effectivePerformedBy = (performedBy != null && !performedBy.isBlank()) ? performedBy : "ANONYMOUS";
        Instant now = Instant.now();
        String normalizedManagerId = req.managerId() != null ? req.managerId().trim().toUpperCase() : null;

        boolean approved = posManagerAuthService.verifyRefundApproval(normalizedManagerId, req.managerPin());

        if (!approved) {
            log.warn("Manager override rejected: managerId={}, action={}, requestedBy={}",
                    normalizedManagerId, req.action(), effectivePerformedBy);

            return new ManagerOverrideResponse(
                    false,
                    normalizedManagerId,
                    req.action(),
                    null,
                    now,
                    "Manager override rejected: invalid credentials or PIN locked"
            );
        }

        PosAuditLog auditLog = PosAuditLog.builder()
                .transactionId(req.targetId())
                .action("MANAGER_OVERRIDE")
                .performedBy(effectivePerformedBy)
                .managerId(normalizedManagerId)
                .reason(req.action())
                .details(req.justification())
                .build();

        PosAuditLog saved = posAuditLogRepository.save(auditLog);

        log.info("Manager override approved: managerId={}, action={}, requestedBy={}, auditLogId={}",
                normalizedManagerId, req.action(), effectivePerformedBy, saved.getId());

        return new ManagerOverrideResponse(
                true,
                normalizedManagerId,
                req.action(),
                saved.getId(),
                now,
                "Manager override approved and audit log created"
        );
    }
}
