package com.example.kms.service;

import java.time.Duration;

public interface GuestCacheService {
    String store(byte[] encryptedBytes, byte[] tempDek, String iv, Duration ttl);
    byte[] retrieveAndConsume(String downloadToken) throws Exception;
    void purgeExpired();
}
