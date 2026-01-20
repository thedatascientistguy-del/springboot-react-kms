package com.example.kms.model;

import jakarta.persistence.*;

@Entity
public class EncryptedData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String encryptedPayload; // Base64 encoded (text, image, or audio)

    @Column(nullable = false)
    private String iv;

    @Column(nullable = false)
    private String salt;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String dekWrapped;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String dekWrappedForRecovery;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataType dataType;

    // ---- Getters & Setters ----
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public String getEncryptedPayload() {
        return encryptedPayload;
    }

    public void setEncryptedPayload(String encryptedPayload) {
        this.encryptedPayload = encryptedPayload;
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

    public String getDekWrapped() {
        return dekWrapped;
    }

    public void setDekWrapped(String dekWrapped) {
        this.dekWrapped = dekWrapped;
    }

    public String getDekWrappedForRecovery() {
        return dekWrappedForRecovery;
    }

    public void setDekWrappedForRecovery(String dekWrappedForRecovery) {
        this.dekWrappedForRecovery = dekWrappedForRecovery;
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }
}
