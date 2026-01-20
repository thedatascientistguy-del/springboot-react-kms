package com.example.kms.dto;

public class DecryptResponse {
    private String dataType;         // TEXT | PHOTO | AUDIO
    private String plaintextBase64;  // For PHOTO/AUDIO return Base64 bytes
    private String text;             // If TEXT, human-readable UTF-8
    private String message;

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getPlaintextBase64() { return plaintextBase64; }
    public void setPlaintextBase64(String plaintextBase64) { this.plaintextBase64 = plaintextBase64; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
