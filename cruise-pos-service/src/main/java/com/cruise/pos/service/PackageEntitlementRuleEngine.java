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

        List<GuestPackageEntitlement> activePackages = entitlementRepository
                .findActiveEntitlementsOrderByPriority(request.getPassengerId());

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

                // Find matching package
                GuestPackageEntitlement matchedPkg = null;
                for (GuestPackageEntitlement pkg : activePackages) {
                    int remaining = virtualQuotaMap.getOrDefault(pkg.getId(), 0);
                    if (remaining > 0 && pkg.isApplicable(item.getCategory(), item.getUnitPrice())) {
                        matchedPkg = pkg;
                        break;
                    }
                }

                if (matchedPkg != null) {
                    int availableQuota = virtualQuotaMap.get(matchedPkg.getId());
                    int coveredUnits = Math.min(item.getQuantity(), availableQuota);
                    virtualQuotaMap.put(matchedPkg.getId(), availableQuota - coveredUnits);

                    BigDecimal unitPrice = item.getUnitPrice();
                    BigDecimal cap = matchedPkg.getMaxItemPriceCap();

                    BigDecimal coveredPerUnit = (cap != null && cap.compareTo(BigDecimal.ZERO) > 0)
                            ? unitPrice.min(cap)
                            : unitPrice;

                    BigDecimal itemCovered = coveredPerUnit.multiply(BigDecimal.valueOf(coveredUnits));
                    BigDecimal itemOutOfPocket = gross.subtract(itemCovered);
                    packageDiscountTotal = packageDiscountTotal.add(itemCovered);

                    boolean fullyCovered = itemOutOfPocket.compareTo(BigDecimal.ZERO) == 0;

                    itemResults.add(EntitlementEvaluateResponse.ItemEntitlementResult.builder()
                            .productId(item.getProductId())
                            .productName(item.getProductName())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .grossAmount(gross)
                            .coveredAmount(itemCovered)
                            .outOfPocketAmount(itemOutOfPocket)
                            .fullyCovered(fullyCovered)
                            .appliedPackageName(matchedPkg.getPackageName())
                            .appliedEntitlementId(matchedPkg.getId())
                            .quotaDeducted(coveredUnits)
                            .build());
                } else {
                    itemResults.add(EntitlementEvaluateResponse.ItemEntitlementResult.builder()
                            .productId(item.getProductId())
                            .productName(item.getProductName())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .grossAmount(gross)
                            .coveredAmount(BigDecimal.ZERO)
                            .outOfPocketAmount(gross)
                            .fullyCovered(false)
                            .quotaDeducted(0)
                            .build());
                }
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
