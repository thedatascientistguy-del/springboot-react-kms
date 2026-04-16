package com.example.kms.service;

import com.example.kms.exception.StorageUnavailableException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class SupabaseStorageServiceImpl implements SupabaseStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(supabaseUrl)
                .build();
    }

    @Override
    public String putObject(String bucket, String objectKey, byte[] encryptedBytes, String contentType) {
        try {
            webClient.put()
                    .uri("/storage/v1/object/{bucket}/{key}", bucket, objectKey)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("Content-Type", contentType)
                    .bodyValue(encryptedBytes)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return objectKey;
        } catch (WebClientResponseException e) {
            throw new StorageUnavailableException(
                    "Failed to upload object: " + objectKey + " — HTTP " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new StorageUnavailableException("Failed to upload object: " + objectKey, e);
        }
    }

    @Override
    public byte[] getObject(String bucket, String objectKey) {
        try {
            return webClient.get()
                    .uri("/storage/v1/object/{bucket}/{key}", bucket, objectKey)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new StorageUnavailableException(
                    "Failed to retrieve object: " + objectKey + " — HTTP " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new StorageUnavailableException("Failed to retrieve object: " + objectKey, e);
        }
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        try {
            webClient.delete()
                    .uri("/storage/v1/object/{bucket}/{key}", bucket, objectKey)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            throw new StorageUnavailableException(
                    "Failed to delete object: " + objectKey + " — HTTP " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new StorageUnavailableException("Failed to delete object: " + objectKey, e);
        }
    }
}
