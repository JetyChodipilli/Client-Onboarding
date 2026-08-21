package com.brainserve.onboarding.assets.application.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Service;

/** Detects common project-asset formats from file signatures and computes a content digest. */
@Service
public class AssetContentInspector {
    private static final int MAGIC_LIMIT = 64 * 1024;

    public Inspection inspect(Path file) {
        try {
            long size = Files.size(file);
            byte[] prefix = readPrefix(file);
            String mime = detect(file, prefix);
            return new Inspection(size, mime, sha256(file));
        } catch (IOException ex) {
            throw new IllegalStateException("Uploaded object could not be inspected", ex);
        }
    }

    private static byte[] readPrefix(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(MAGIC_LIMIT);
        }
    }

    private static String detect(Path file, byte[] b) throws IOException {
        if (starts(b, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
        if (starts(b, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (ascii(b, 0, "GIF87a") || ascii(b, 0, "GIF89a")) return "image/gif";
        if (ascii(b, 0, "%PDF-")) return "application/pdf";
        if (b.length >= 12 && ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP")) return "image/webp";
        if (b.length >= 12 && ascii(b, 4, "ftyp")) return "video/mp4";
        if (starts(b, 0x50, 0x4B, 0x03, 0x04) || starts(b, 0x50, 0x4B, 0x05, 0x06)) return detectZip(file);
        if (looksLikeUtf8Text(b)) return "text/plain";
        return "application/octet-stream";
    }

    private static String detectZip(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            if (zip.getEntry("[Content_Types].xml") != null) {
                if (zip.stream().anyMatch(e -> e.getName().startsWith("word/"))) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                if (zip.stream().anyMatch(e -> e.getName().startsWith("xl/"))) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                if (zip.stream().anyMatch(e -> e.getName().startsWith("ppt/"))) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            }
        }
        return "application/zip";
    }

    private static boolean looksLikeUtf8Text(byte[] bytes) {
        if (bytes.length == 0) return false;
        for (byte value : bytes) if (value == 0) return false;
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException ex) { return false; }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        byte[] value = expected.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + value.length) return false;
        for (int i = 0; i < value.length; i++) if (bytes[offset + i] != value[i]) return false;
        return true;
    }

    private static boolean starts(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if ((bytes[i] & 0xff) != expected[i]) return false;
        return true;
    }

    public record Inspection(long sizeBytes, String detectedMimeType, String sha256) {}
}
