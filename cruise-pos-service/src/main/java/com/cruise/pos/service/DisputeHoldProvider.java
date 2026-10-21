package com.cruise.pos.service;

/**
 * Cổng mở rộng để module khiếu nại (reconciliation/dispute) cắm vào luồng tất toán.
 * Trên nhánh main hiện chưa có module dispute nên mặc định trả về không có khoản giữ.
 */
public interface DisputeHoldProvider {

    DisputeHold resolve(String cabinNumber);
}
