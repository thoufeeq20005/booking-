package com.bookeasy.file;

public enum FileVisibility {
    /** Anyone may download it by its public id (logos, gallery images). */
    PUBLIC,
    /** Only streamed through endpoints that check the caller's right to the owning record. */
    PRIVATE
}
