package com.example.kms.service;

import com.example.kms.repository.ClientRepository;
import com.example.kms.util.HashUtil;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.example.kms.model.Client;

@Service
public class ClientOtpService {

    private final ClientRepository clientRepository;
    private final JavaMailSender mailSender;

    // In-memory OTP cache
    private final Map<String, OtpEntry> otpCache = new ConcurrentHashMap<>();
    private static final long EXPIRY_SECONDS = 300; // 5 min

    public ClientOtpService(ClientRepository clientRepository, JavaMailSender mailSender) {
        this.clientRepository = clientRepository;
        this.mailSender = mailSender;
    }

    // ✅ Generate 6-digit OTP
    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    // ✅ Create, save in cache, and send OTP email
public String createAndSendOtp(String rawEmail) {
    if (rawEmail == null || rawEmail.isBlank()) {
        throw new IllegalArgumentException("Email must not be empty");
    }

    // Normalize email same as registration (if you used lowercase there)
    String hashedEmail = HashUtil.sha256(rawEmail);

    // Lookup client by hashed email
    clientRepository.findByEmailHash(hashedEmail)
            .orElseThrow(() -> new IllegalArgumentException("Client not found for email: " + rawEmail));

    String otp = generateOtp();
    otpCache.put(rawEmail, new OtpEntry(otp, Instant.now().plusSeconds(EXPIRY_SECONDS)));

    // Send OTP to the actual raw email
    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setTo(rawEmail);
    msg.setSubject("Your OTP Code");
    msg.setText("Your OTP is: " + otp + "\n\nThis code will expire in 5 minutes.");
    mailSender.send(msg);

    return otp;
}



    // ✅ Validate OTP
    public boolean validateOtp(String email, String otp) {
        OtpEntry entry = otpCache.get(email);
        if (entry == null) return false;

        if (Instant.now().isAfter(entry.expiry)) {
            otpCache.remove(email);
            return false;
        }

        boolean valid = entry.value.equals(otp);
        if (valid) otpCache.remove(email); // one-time use
        return valid;
    }

    // Inner cache entry
    private static class OtpEntry {
        String value;
        Instant expiry;
        OtpEntry(String value, Instant expiry) {
            this.value = value;
            this.expiry = expiry;
        }
    }
}
