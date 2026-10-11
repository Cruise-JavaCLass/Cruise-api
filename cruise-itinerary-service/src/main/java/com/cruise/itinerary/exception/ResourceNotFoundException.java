package com.cruise.itinerary.exception;

/**
 * Ngoại lệ ném ra khi không tìm thấy tài nguyên yêu cầu (HTTP 404)
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
