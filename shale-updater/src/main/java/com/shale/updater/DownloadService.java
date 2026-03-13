package com.shale.updater;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;

public final class DownloadService {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public Path downloadToTemp(String fileUrl, String fileName, String expectedSha256)
            throws IOException, InterruptedException {

        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "ShaleUpdater");
        Files.createDirectories(tempDir);

        Path target = tempDir.resolve(fileName);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<InputStream> response =
                http.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() / 100 != 2) {
            throw new IOException("Download failed: HTTP " + response.statusCode());
        }

        try (InputStream in = response.body()) {
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        // SHA256 verification
        if (expectedSha256 != null && !expectedSha256.isBlank()) {

            System.out.println("Verifying SHA256...");

            try {
                String actualHash = sha256(target);

                if (!expectedSha256.equalsIgnoreCase(actualHash)) {
                    throw new IOException(
                            "Update package integrity check failed.\nExpected: "
                                    + expectedSha256 + "\nActual: " + actualHash);
                }

                System.out.println("SHA256 verification passed.");
            } catch (Exception e) {
                throw new IOException("Failed to verify update package hash", e);
            }
        }

        return target;
    }

    private static String sha256(Path file) throws Exception {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream is = Files.newInputStream(file)) {

            byte[] buffer = new byte[8192];
            int read;

            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }

        byte[] hash = digest.digest();

        StringBuilder hex = new StringBuilder();

        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }

        return hex.toString();
    }
}