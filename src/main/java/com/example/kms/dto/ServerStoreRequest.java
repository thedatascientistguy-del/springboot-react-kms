package com.example.kms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ServerStoreRequest {

    @NotBlank
    private String plaintextBase64; // base64-encoded bytes (text, image, audio etc)

    @NotBlank
    @Pattern(regexp = "TEXT|PHOTO|AUDIO", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String dataType; // TEXT | PHOTO | AUDIO

    // optional: wrap DEK also for a recovery public key
    private String recoveryPublicKeyBase64;

    // ---- Getters & Setters ----
    public String getPlaintextBase64() {
        return plaintextBase64;
    }

    public void setPlaintextBase64(String plaintextBase64) {
        this.plaintextBase64 = plaintextBase64;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getRecoveryPublicKeyBase64() {
        return recoveryPublicKeyBase64;
    }

    public void setRecoveryPublicKeyBase64(String recoveryPublicKeyBase64) {
        this.recoveryPublicKeyBase64 = recoveryPublicKeyBase64;
    }
}
