package com.example.kms.controller;

import com.example.kms.dto.OtpRequest;
import com.example.kms.dto.OtpVerifyRequest;
import com.example.kms.service.ClientOtpService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/otp")
public class ClientOtpController {

    private final ClientOtpService clientOtpService;

    public ClientOtpController(ClientOtpService clientOtpService) {
        this.clientOtpService = clientOtpService;
    }

    // ✅ Request OTP
    @PostMapping("/request")
    public ResponseEntity<String> requestOtp(@Valid @RequestBody OtpRequest req) {
        String otp = clientOtpService.createAndSendOtp(req.getEmail());

        // ⚠️ For now, we return OTP in response (only for testing).
        // In production, you should send via SMS/Email and NOT return it.
        return ResponseEntity.ok("OTP generated: " + otp);
    }

    // ✅ Verify OTP
    @PostMapping("/verify")
    public ResponseEntity<String> verifyOtp(@Valid @RequestBody OtpVerifyRequest req) {
        boolean valid = clientOtpService.validateOtp(req.getEmail(), req.getOtp());

        if (valid) {
            return ResponseEntity.ok("OTP verified successfully");
        } else {
            return ResponseEntity.badRequest().body("Invalid or expired OTP");
        }
    }
}
