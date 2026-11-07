package com.bookeasy.file;

import java.time.Duration;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** Builds download responses with headers that keep stored files from being executed or cached in shared caches. */
@Component
public class FileResponses {

    private final FileService fileService;

    public FileResponses(FileService fileService) {
        this.fileService = fileService;
    }

    /** Private files: never cached by intermediaries, offered as a download unless the browser can preview them. */
    public ResponseEntity<Resource> privateFile(UploadedFile file) {
        return build(file, CacheControl.noStore().cachePrivate());
    }

    /** Public files have random, immutable ids, so they can be cached for a long time. */
    public ResponseEntity<Resource> publicFile(UploadedFile file) {
        return build(file, CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable());
    }

    private ResponseEntity<Resource> build(UploadedFile file, CacheControl cache) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSizeBytes())
                .cacheControl(cache)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.getOriginalName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                // Only our own sniffed types are served, but forbid any active content regardless.
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; frame-ancestors 'none'; sandbox")
                .body(new InputStreamResource(fileService.open(file)));
    }
}
