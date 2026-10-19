package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.FolioAccountResponse;
import com.cruise.pos.service.PosFolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pos/folios")
@RequiredArgsConstructor
public class PosFolioController {

    private final PosFolioService folioService;

    @GetMapping("/{cabinNumber}")
    public ApiResponse<FolioAccountResponse> getFolio(@PathVariable("cabinNumber") String cabinNumber) {
        FolioAccountResponse response = folioService.getFolioByCabin(cabinNumber);
        return ApiResponse.ok(response);
    }
}
