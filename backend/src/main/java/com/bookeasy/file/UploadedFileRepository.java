package com.bookeasy.file;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {

    Optional<UploadedFile> findByPublicIdAndVisibility(String publicId, FileVisibility visibility);
}
