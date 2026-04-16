package com.example.kms.service;

public interface SupabaseStorageService {
    String putObject(String bucket, String objectKey, byte[] encryptedBytes, String contentType);
    byte[] getObject(String bucket, String objectKey);
    void deleteObject(String bucket, String objectKey);
}
