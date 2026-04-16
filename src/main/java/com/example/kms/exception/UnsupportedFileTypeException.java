package com.example.kms.exception;

public class UnsupportedFileTypeException extends RuntimeException {
    public UnsupportedFileTypeException(String mimeType) {
        super("Unsupported file type: " + mimeType);
    }
}
