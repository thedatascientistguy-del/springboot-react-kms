package com.example.kms.model;

import com.example.kms.util.EncryptDecryptConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = EncryptDecryptConverter.class)
    @Column(nullable = false)
    private String name;

    @Convert(converter = EncryptDecryptConverter.class)
    @Column(nullable = false, unique = true)
    private String phone;

    // 🔹 Encrypted email for confidentiality
    @Convert(converter = EncryptDecryptConverter.class)
    @Column(nullable = false)
    private String email;

    // 🔹 SHA-256 hash of lowercase(email) for lookup
    @Column(nullable = false, unique = true)
    private String emailHash;

    @Column(nullable = false)
    private String password; // stored as BCrypt hash

    @Lob
    @Column(name = "public_key", nullable = false, columnDefinition = "CLOB")
    private String publicKey;

    @Lob
    @Column(name = "server_public_key", nullable = false, columnDefinition = "CLOB")
    private String serverPublicKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // 🔹 OTP fields
    @Column(name = "otp_code")
    private String otpCode;

    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;

    public Client() {}

    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getEmailHash() { return emailHash; }
    public void setEmailHash(String emailHash) { this.emailHash = emailHash; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getServerPublicKey() { return serverPublicKey; }
    public void setServerPublicKey(String serverPublicKey) { this.serverPublicKey = serverPublicKey; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }

    public LocalDateTime getOtpExpiry() { return otpExpiry; }
    public void setOtpExpiry(LocalDateTime otpExpiry) { this.otpExpiry = otpExpiry; }
}
