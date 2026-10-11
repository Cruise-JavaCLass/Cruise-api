package com.cruise.itinerary.exception;

/**
 * Ngoại lệ ném ra khi tài nguyên bị trùng lặp mã hiệu hoặc tên (HTTP 409)
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
