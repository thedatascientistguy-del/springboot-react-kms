package com.example.kms.controller;

import com.example.kms.dto.DecryptRequest;
import com.example.kms.dto.DecryptResponse;
import com.example.kms.service.DecryptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;

@RestController
@RequestMapping("/api/clients")
public class DecryptController {

    private final DecryptService decryptService;

    public DecryptController(DecryptService decryptService) {
        this.decryptService = decryptService;
    }

    // URL: POST /api/clients/{email}/decrypt/{recordId}
    @PostMapping("/{email}/decrypt/{recordId}")
    public ResponseEntity<?> decryptRecord(@PathVariable("email") String email,
                                           @PathVariable("recordId") Long recordId,
                                           @RequestBody DecryptRequest req) {
        try {
            DecryptService.DecryptResult result = decryptService.decryptRecord(
                    email, recordId, req.getClientPublicKeyBase64(), req.getServerPublicKeyBase64()
            );

            byte[] plaintext = result.getPlaintextBytes();
            String dataType = result.getDataType();

            DecryptResponse resp = new DecryptResponse();
            resp.setDataType(dataType);
            resp.setPlaintextBase64(Base64.getEncoder().encodeToString(plaintext));
            if ("TEXT".equalsIgnoreCase(dataType)) {
                resp.setText(new String(plaintext, java.nio.charset.StandardCharsets.UTF_8));
            }
            resp.setMessage("Decryption successful");
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            DecryptResponse err = new DecryptResponse();
            err.setMessage("Bad request: " + iae.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            DecryptResponse err = new DecryptResponse();
            err.setMessage("Decryption failed: " + e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }
}
