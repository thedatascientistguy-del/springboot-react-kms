package com.example.kms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ClientRegisterRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "^[0-9]{7,15}$", message = "phone must be 7-15 digits")
    private String phone;

    @NotBlank
    @Email(message = "invalid email format")
    private String email;

    @NotBlank
    @Size(min = 8, message = "password must be at least 8 characters long")
    private String password;

    // optional: client can provide public key or let server generate
    private String publicKey;

    // optional: server public key (for test) otherwise server generates one
    private String serverPublicKey;

    // ---- Getters & Setters ----
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getServerPublicKey() { return serverPublicKey; }
    public void setServerPublicKey(String serverPublicKey) { this.serverPublicKey = serverPublicKey; }
}
