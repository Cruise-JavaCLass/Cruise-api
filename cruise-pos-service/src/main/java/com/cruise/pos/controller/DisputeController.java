package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.CreateDisputeRequest;
import com.cruise.pos.dto.DisputeCaseDto;
import com.cruise.pos.dto.ResolveDisputeRequest;
import com.cruise.pos.enums.DisputeStatus;
import com.cruise.pos.service.DisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pos/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    @PostMapping
    public ResponseEntity<ApiResponse<DisputeCaseDto>> createDispute(@Valid @RequestBody CreateDisputeRequest request) {
        DisputeCaseDto created = disputeService.createDispute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo yêu cầu khiếu nại chi phí thành công", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DisputeCaseDto>>> getDisputes(
            @RequestParam(required = false) DisputeStatus status,
            @RequestParam(required = false) String cabinNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<DisputeCaseDto> disputes = disputeService.getDisputes(
                status, cabinNumber, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.ok(disputes));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DisputeCaseDto>> getDisputeById(@PathVariable Long id) {
        DisputeCaseDto dispute = disputeService.getDisputeById(id);
        return ResponseEntity.ok(ApiResponse.ok(dispute));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<DisputeCaseDto>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        DisputeStatus newStatus = DisputeStatus.valueOf(body.get("status"));
        String actorId = body.getOrDefault("actorId", "STAFF");
        String note = body.getOrDefault("note", "Cập nhật tiến độ điều tra");
        DisputeCaseDto updated = disputeService.updateStatus(id, newStatus, actorId, note);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái khiếu nại thành công", updated));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<DisputeCaseDto>> resolveDispute(
            @PathVariable Long id,
            @Valid @RequestBody ResolveDisputeRequest request) {
        DisputeCaseDto resolved = disputeService.resolveDispute(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Quyết định giải quyết khiếu nại thành công", resolved));
    }
}
