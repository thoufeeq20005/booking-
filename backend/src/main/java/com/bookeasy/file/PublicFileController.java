package com.bookeasy.file;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.common.exception.ApiException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Serves PUBLIC files only. A private file's id returns 404 here exactly like an unknown id. */
@RestController
@RequestMapping("/api/v1/public/files")
@Tag(name = "Files")
@SecurityRequirements
public class PublicFileController {

    private final UploadedFileRepository files;
    private final FileResponses responses;

    public PublicFileController(UploadedFileRepository files, FileResponses responses) {
        this.files = files;
        this.responses = responses;
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "Download a public file such as a business logo")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> get(@PathVariable String publicId) {
        UploadedFile file = files.findByPublicIdAndVisibility(publicId, FileVisibility.PUBLIC)
                .orElseThrow(() -> ApiException.notFound("File"));
        return responses.publicFile(file);
    }
}
