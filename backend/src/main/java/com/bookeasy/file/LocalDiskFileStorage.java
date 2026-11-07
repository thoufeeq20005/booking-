package com.bookeasy.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.stereotype.Component;

import com.bookeasy.common.config.BookEasyProperties;

/** Stores files under {@code bookeasy.storage.root-path}. Writes go to a temp file first, then move atomically. */
@Component
public class LocalDiskFileStorage implements FileStorage {

    private final Path root;

    public LocalDiskFileStorage(BookEasyProperties properties) throws IOException {
        this.root = Path.of(properties.storage().rootPath()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root) || p.equals(root)) {
            throw new IllegalArgumentException("Storage key escapes the storage root");
        }
        return p;
    }

    @Override
    public void store(String key, InputStream content) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try {
            Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
            // No REPLACE_EXISTING: an existing key is a bug, not something to overwrite silently.
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }
}
