package com.cruise.activity.dining.service;

public class TableNotAvailableException extends RuntimeException {

    public TableNotAvailableException(String message) {
        super(message);
    }
}