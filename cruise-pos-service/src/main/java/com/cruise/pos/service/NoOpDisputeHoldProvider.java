package com.cruise.pos.service;

import org.springframework.stereotype.Component;

/**
 * Mặc định: không có khoản khiếu nại nào bị giữ.
 * Khi module dispute được merge, thay thế bean này bằng implementation thật.
 */
@Component
public class NoOpDisputeHoldProvider implements DisputeHoldProvider {

    @Override
    public DisputeHold resolve(String cabinNumber) {
        return DisputeHold.none();
    }
}
