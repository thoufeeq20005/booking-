package com.bookeasy.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;

/**
 * Validates and stores uploads, keeping disk and database consistent: bytes written for a transaction that
 * rolls back are removed, and bytes of deleted records are removed only after the deletion commits.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final DateTimeFormatter MONTH_PATH = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);

    private final FileStorage storage;
    private final UploadedFileRepository repository;
    private final Clock clock;

    public FileService(FileStorage storage, UploadedFileRepository repository, Clock clock) {
        this.storage = storage;
        this.repository = repository;
        this.clock = clock;
    }

    /** What an upload slot accepts. */
    public record UploadRules(Set<String> allowedTypes, long maxBytes) {
    }

    /**
     * Checks the real type (magic bytes) and size, then stores the file and its metadata row.
     * Must run inside the caller's transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UploadedFile store(MultipartFile upload, UploadRules rules, FileVisibility visibility, FilePurpose purpose,
                              Long ownerUserId) {
        if (upload == null || upload.isEmpty()) {
            throw new ApiException(ErrorCode.FILE_EMPTY);
        }
        if (upload.getSize() > rules.maxBytes()) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        Instant now = clock.instant();
        try (InputStream in = upload.getInputStream()) {
            byte[] head = in.readNBytes(ContentSniffer.HEADER_BYTES);
            String type = ContentSniffer.detect(head)
                    .filter(rules.allowedTypes()::contains)
                    .orElseThrow(() -> new ApiException(ErrorCode.FILE_TYPE_NOT_ALLOWED));

            String publicId = UUID.randomUUID().toString();
            String key = visibility.name().toLowerCase() + "/" + MONTH_PATH.format(now) + "/" + publicId + "."
                    + ContentSniffer.extension(type);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream all = new DigestInputStream(
                    new SequenceInputStream(new ByteArrayInputStream(head), in), sha)) {
                storage.store(key, all);
            }
            deleteOnRollback(key);
            return repository.save(new UploadedFile(publicId, key, safeName(upload.getOriginalFilename(), type), type,
                    upload.getSize(), HexFormat.of().formatHex(sha.digest()), visibility, purpose, ownerUserId, now));
        } catch (IOException e) {
            throw new IllegalStateException("Could not store upload", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Deletes the metadata row now and the bytes once the transaction commits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(UploadedFile file) {
        repository.delete(file);
        String key = file.getStorageKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                removeQuietly(key);
            }
        });
    }

    public InputStream open(UploadedFile file) {
        try {
            return storage.open(file.getStorageKey());
        } catch (IOException e) {
            log.error("Stored file {} is missing or unreadable", file.getStorageKey(), e);
            throw ApiException.notFound("File");
        }
    }

    private void deleteOnRollback(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    removeQuietly(key);
                }
            }
        });
    }

    private void removeQuietly(String key) {
        try {
            storage.delete(key);
        } catch (IOException e) {
            log.warn("Could not delete stored file {}", key, e);
        }
    }

    /**
     * The original name is only ever shown back to people (and used in Content-Disposition), so keep it
     * printable, path-free and short, and make its extension match the detected type.
     */
    static String safeName(String original, String type) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|:*?]", "").strip();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        if (base.isBlank()) {
            base = "document";
        }
        if (base.length() > 120) {
            base = base.substring(0, 120);
        }
        return base + "." + ContentSniffer.extension(type);
    }
}
