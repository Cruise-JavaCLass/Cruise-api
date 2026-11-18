package com.cruise.auth.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation kiểm tra quyền sở hữu của hành khách hoặc vai trò quản lý / lễ tân (KAN-720).
 * Cho phép các vai trò quản lý (SYSTEM_ADMIN, OPERATIONS_MANAGER, FINANCE_FRONT_DESK)
 * hoặc chính hành khách sở hữu hồ sơ hành khách đó (thông qua bảng liên kết user_passenger_bindings).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@passengerOwnershipValidator.isOwnerOrStaff(authentication, #id)")
public @interface RequirePassengerOwnership {
}
