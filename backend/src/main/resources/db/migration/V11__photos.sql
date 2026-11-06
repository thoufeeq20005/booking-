-- BookEasy: a cover photo for each business (shown on search cards and the business page) and a photo per service.
ALTER TABLE organizations
    ADD COLUMN cover_file_id BIGINT NULL,
    ADD CONSTRAINT fk_organizations_cover FOREIGN KEY (cover_file_id) REFERENCES uploaded_files (id) ON DELETE SET NULL;

ALTER TABLE services
    ADD COLUMN image_file_id BIGINT NULL,
    ADD CONSTRAINT fk_services_image FOREIGN KEY (image_file_id) REFERENCES uploaded_files (id) ON DELETE SET NULL;
