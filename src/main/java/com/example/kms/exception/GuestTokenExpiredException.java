package com.example.kms.exception;

public class GuestTokenExpiredException extends RuntimeException {
    public GuestTokenExpiredException(String message) {
        super(message);
    }
}
