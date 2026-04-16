package com.example.kms.model;

import com.example.kms.exception.UnsupportedFileTypeException;

import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;

public enum FileCategory {
    DOCUMENT, IMAGE, VIDEO, AUDIO;

    private static final Map<String, FileCategory> MIME_MAP = Map.ofEntries(
        // Documents
        entry("application/pdf", DOCUMENT),
        entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", DOCUMENT),
        entry("application/msword", DOCUMENT),
        entry("text/plain", DOCUMENT),
        entry("text/csv", DOCUMENT),
        entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", DOCUMENT),
        // Images
        entry("image/jpeg", IMAGE),
        entry("image/png", IMAGE),
        entry("image/webp", IMAGE),
        entry("image/gif", IMAGE),
        entry("image/bmp", IMAGE),
        // Audio
        entry("audio/mpeg", AUDIO),
        entry("audio/wav", AUDIO),
        entry("audio/flac", AUDIO),
        entry("audio/aac", AUDIO),
        entry("audio/ogg", AUDIO),
        // Video
        entry("video/mp4", VIDEO),
        entry("video/x-msvideo", VIDEO),
        entry("video/quicktime", VIDEO),
        entry("video/x-matroska", VIDEO)
    );

    public static FileCategory fromMimeType(String mimeType) {
        return Optional.ofNullable(MIME_MAP.get(mimeType.toLowerCase()))
            .orElseThrow(() -> new UnsupportedFileTypeException(mimeType));
    }
}
