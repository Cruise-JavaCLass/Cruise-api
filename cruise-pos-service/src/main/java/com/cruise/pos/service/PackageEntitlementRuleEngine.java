package com.cruise.pos.service;

import com.cruise.pos.dto.EntitlementEvaluateRequest;
import com.cruise.pos.dto.EntitlementEvaluateResponse;
import com.cruise.pos.entity.GuestPackageEntitlement;
import com.cruise.pos.repository.GuestPackageEntitlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PackageEntitlementRuleEngine {

    private final GuestPackageEntitlementRepository entitlementRepository;

    @Transactional(readOnly = true)
    public EntitlementEvaluateResponse evaluateCartEntitlements(EntitlementEvaluateRequest request) {
        Objects.requireNonNull(request, "EntitlementEvaluateRequest must not be null");

        List<GuestPackageEntitlement> fetched = entitlementRepository
                .findActiveEntitlementsOrderByPriority(request.getPassengerId());

        List<GuestPackageEntitlement> activePackages = new ArrayList<>(fetched != null ? fetched : Collections.emptyList());

        // Sort packages consistent with collision resolution: Priority desc, then validTo asc, then id asc
        activePackages.sort((a, b) -> {
            int pCompare = Integer.compare(b.getPriorityLevel(), a.getPriorityLevel());
            if (pCompare != 0) return pCompare;
            if (a.getValidTo() != null && b.getValidTo() != null) {
                return a.getValidTo().compareTo(b.getValidTo());
            }
            if (a.getValidTo() != null) return -1;
            if (b.getValidTo() != null) return 1;
            long idA = a.getId() != null ? a.getId() : 0L;
            long idB = b.getId() != null ? b.getId() : 0L;
            return Long.compare(idA, idB);
        });

        // Map to keep track of available virtual quota during evaluation
        Map<Long, Integer> virtualQuotaMap = new HashMap<>();
        for (GuestPackageEntitlement pkg : activePackages) {
            virtualQuotaMap.put(pkg.getId(), pkg.getRemainingQuota());
        }

        BigDecimal originalTotal = BigDecimal.ZERO;
        BigDecimal packageDiscountTotal = BigDecimal.ZERO;
        List<EntitlementEvaluateResponse.ItemEntitlementResult> itemResults = new ArrayList<>();

        if (request.getItems() != null) {
            for (EntitlementEvaluateRequest.EvaluateItem item : request.getItems()) {
                BigDecimal gross = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                originalTotal = originalTotal.add(gross);

                int remainingQty = item.getQuantity();
                BigDecimal totalItemCovered = BigDecimal.ZERO;
                String lastAppliedPkgName = null;
                Long lastAppliedEntitlementId = null;
                int totalQuotaDeducted = 0;

                // Loop through applicable packages until covered or no quota left
                for (GuestPackageEntitlement pkg : activePackages) {
                    if (remainingQty <= 0) break;

                    int availableQuota = virtualQuotaMap.getOrDefault(pkg.getId(), 0);
                    if (availableQuota > 0 && pkg.isApplicable(item.getCategory(), item.getUnitPrice())) {
                        int unitsToCover = Math.min(remainingQty, availableQuota);
                        virtualQuotaMap.put(pkg.getId(), availableQuota - unitsToCover);
                        remainingQty -= unitsToCover;
                        totalQuotaDeducted += unitsToCover;

                        lastAppliedPkgName = pkg.getPackageName();
                        lastAppliedEntitlementId = pkg.getId();

                        BigDecimal cap = pkg.getMaxItemPriceCap();
                        BigDecimal coveredPerUnit = (cap != null && cap.compareTo(BigDecimal.ZERO) > 0)
                                ? item.getUnitPrice().min(cap)
                                : item.getUnitPrice();
                        BigDecimal coveredPart = coveredPerUnit.multiply(BigDecimal.valueOf(unitsToCover));
                        totalItemCovered = totalItemCovered.add(coveredPart);
                    }
                }

                packageDiscountTotal = packageDiscountTotal.add(totalItemCovered);
                BigDecimal itemOutOfPocket = gross.subtract(totalItemCovered).max(BigDecimal.ZERO);
                boolean fullyCovered = itemOutOfPocket.compareTo(BigDecimal.ZERO) == 0;

                itemResults.add(EntitlementEvaluateResponse.ItemEntitlementResult.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .grossAmount(gross)
                        .coveredAmount(totalItemCovered)
                        .outOfPocketAmount(itemOutOfPocket)
                        .fullyCovered(fullyCovered)
                        .appliedPackageName(lastAppliedPkgName)
                        .appliedEntitlementId(lastAppliedEntitlementId)
                        .quotaDeducted(totalQuotaDeducted)
                        .build());
            }
        }

        BigDecimal netPayable = originalTotal.subtract(packageDiscountTotal).max(BigDecimal.ZERO);

        return EntitlementEvaluateResponse.builder()
                .passengerId(request.getPassengerId())
                .cabinNumber(request.getCabinNumber())
                .originalTotal(originalTotal)
                .packageDiscountTotal(packageDiscountTotal)
                .netPayableTotal(netPayable)
                .items(itemResults)
                .build();
    }
}
