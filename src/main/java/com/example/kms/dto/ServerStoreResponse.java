package com.example.kms.dto;

public class ServerStoreResponse {

    private Long encryptedDataId;
    private String dekWrappedForClient;
    private String dekWrappedForRecovery;
    private String iv;
    private String salt;
    private String serverPublicKey;

    // ---- Getters & Setters ----
    public Long getEncryptedDataId() {
        return encryptedDataId;
    }

    public void setEncryptedDataId(Long encryptedDataId) {
        this.encryptedDataId = encryptedDataId;
    }

    public String getDekWrappedForClient() {
        return dekWrappedForClient;
    }

    public void setDekWrappedForClient(String dekWrappedForClient) {
        this.dekWrappedForClient = dekWrappedForClient;
    }

    public String getDekWrappedForRecovery() {
        return dekWrappedForRecovery;
    }

    public void setDekWrappedForRecovery(String dekWrappedForRecovery) {
        this.dekWrappedForRecovery = dekWrappedForRecovery;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getServerPublicKey() {
        return serverPublicKey;
    }

    public void setServerPublicKey(String serverPublicKey) {
        this.serverPublicKey = serverPublicKey;
    }
}
