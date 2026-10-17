package com.cruise.activity.controller;

import com.cruise.activity.dto.PassengerCatalogItemResponse;
import com.cruise.activity.enums.CatalogItemType;
import com.cruise.activity.service.PassengerCatalogService;
import com.cruise.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

/**
 * Controller phục vụ cổng thông tin hành khách duyệt Hoạt động & Tour bờ (KAN-211)
 */
@RestController
@RequestMapping("/api/passenger-portal/catalog")
@RequiredArgsConstructor
public class PassengerCatalogController {

    private final PassengerCatalogService catalogService;

    @GetMapping("/tour/{tourId}")
    public ApiResponse<Page<PassengerCatalogItemResponse>> searchCatalog(
            @PathVariable Long tourId,
            @RequestParam(required = false) Integer dayNumber,
            @RequestParam(required = false) Long portId,
            @RequestParam(required = false) CatalogItemType itemType,
            @RequestParam(required = false) Boolean isIncluded,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PassengerCatalogItemResponse> result = catalogService.searchCatalog(
                tourId, dayNumber, portId, itemType, isIncluded, keyword, pageable
        );
        return ApiResponse.ok("Lấy danh mục hoạt động và tour bờ thành công", result);
    }
}
