package com.example.kms.dto;

public class FetchResponse {

    private String email;
    private String encryptedData;
    private String iv;
    private String salt;
    private String dekWrappedForClient;
    private String dekWrappedForRecovery;
    private String serverPublicKey;
    private String dataType;

    // ---- Getters & Setters ----
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getEncryptedData() { return encryptedData; }
    public void setEncryptedData(String encryptedData) { this.encryptedData = encryptedData; }

    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public String getDekWrappedForClient() { return dekWrappedForClient; }
    public void setDekWrappedForClient(String dekWrappedForClient) { this.dekWrappedForClient = dekWrappedForClient; }

    public String getDekWrappedForRecovery() { return dekWrappedForRecovery; }
    public void setDekWrappedForRecovery(String dekWrappedForRecovery) { this.dekWrappedForRecovery = dekWrappedForRecovery; }

    public String getServerPublicKey() { return serverPublicKey; }
    public void setServerPublicKey(String serverPublicKey) { this.serverPublicKey = serverPublicKey; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
}
