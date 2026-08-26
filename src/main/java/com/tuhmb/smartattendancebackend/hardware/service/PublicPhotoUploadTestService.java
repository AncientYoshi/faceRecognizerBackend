package com.tuhmb.smartattendancebackend.hardware.service;

import com.tuhmb.smartattendancebackend.face.exception.ImageValidationException;
import com.tuhmb.smartattendancebackend.hardware.api.PublicPhotoUploadTestResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class PublicPhotoUploadTestService {

    private static final String OUTPUT_FILENAME = "captured.jpg";

    private final Path directory;
    private final long maxBytes;

    public PublicPhotoUploadTestService(
            @Value("${app.photo-upload-test.directory:/tmp/smart-attendance-upload-test}") String directory,
            @Value("${app.photo-upload-test.max-bytes:5242880}") long maxBytes
    ) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        if (maxBytes < 1) {
            throw new IllegalArgumentException("Photo upload test maximum size must be positive");
        }
    }

    public PublicPhotoUploadTestResponse save(InputStream input, long declaredLength) {
        if (declaredLength == 0) {
            throw invalidImage("image_required", "A non-empty JPEG body is required");
        }
        if (declaredLength > maxBytes) {
            throw tooLarge();
        }

        Path temporaryFile = null;
        try {
            Files.createDirectories(directory);
            temporaryFile = Files.createTempFile(directory, "captured-", ".jpg.part");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            UploadCopy copy = copy(input, temporaryFile, digest);
            validateJpeg(copy);

            Path output = directory.resolve(OUTPUT_FILENAME);
            moveAtomicallyWhenSupported(temporaryFile, output);
            temporaryFile = null;
            return new PublicPhotoUploadTestResponse(
                    "OK",
                    copy.bytes(),
                    HexFormat.of().formatHex(digest.digest()),
                    output.toString(),
                    Instant.now()
            );
        } catch (ImageValidationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save uploaded test photo", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    // The original upload failure is more useful to the caller.
                }
            }
        }
    }

    private UploadCopy copy(InputStream input, Path destination, MessageDigest digest) throws IOException {
        byte[] prefix = new byte[3];
        int prefixLength = 0;
        long total = 0;
        byte[] buffer = new byte[8 * 1024];
        try (OutputStream output = Files.newOutputStream(destination)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (total + read > maxBytes) {
                    throw tooLarge();
                }
                int prefixBytes = Math.min(read, prefix.length - prefixLength);
                if (prefixBytes > 0) {
                    System.arraycopy(buffer, 0, prefix, prefixLength, prefixBytes);
                    prefixLength += prefixBytes;
                }
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                total += read;
            }
        }
        return new UploadCopy(total, prefix, prefixLength);
    }

    private void validateJpeg(UploadCopy copy) {
        if (copy.bytes() == 0) {
            throw invalidImage("image_required", "A non-empty JPEG body is required");
        }
        if (copy.prefixLength() < 3
                || Byte.toUnsignedInt(copy.prefix()[0]) != 0xFF
                || Byte.toUnsignedInt(copy.prefix()[1]) != 0xD8
                || Byte.toUnsignedInt(copy.prefix()[2]) != 0xFF) {
            throw invalidImage("invalid_jpeg", "Request body is not a valid JPEG image");
        }
    }

    private void moveAtomicallyWhenSupported(Path source, Path destination) throws IOException {
        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ImageValidationException tooLarge() {
        return new ImageValidationException(
                HttpStatus.CONTENT_TOO_LARGE,
                "image_too_large",
                "JPEG body must not exceed " + maxBytes + " bytes"
        );
    }

    private ImageValidationException invalidImage(String code, String message) {
        return new ImageValidationException(HttpStatus.BAD_REQUEST, code, message);
    }

    private record UploadCopy(long bytes, byte[] prefix, int prefixLength) {
    }
}
