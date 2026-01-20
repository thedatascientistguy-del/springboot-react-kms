package com.example.kms.dto;

public class DecryptRequest {
    private String clientPublicKeyBase64;
    private String serverPublicKeyBase64;

    public String getClientPublicKeyBase64() {
        return clientPublicKeyBase64;
    }
    public void setClientPublicKeyBase64(String clientPublicKeyBase64) {
        this.clientPublicKeyBase64 = clientPublicKeyBase64;
    }

    public String getServerPublicKeyBase64() {
        return serverPublicKeyBase64;
    }
    public void setServerPublicKeyBase64(String serverPublicKeyBase64) {
        this.serverPublicKeyBase64 = serverPublicKeyBase64;
    }
}
