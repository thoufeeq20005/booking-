-- Deleting a business removes its venue profile and its hidden booking service in one cascade; MySQL may
-- reach the service first, so the profile's link to it must cascade too.
ALTER TABLE venue_profiles DROP FOREIGN KEY fk_venue_service;
ALTER TABLE venue_profiles
    ADD CONSTRAINT fk_venue_service FOREIGN KEY (booking_service_id) REFERENCES services (id) ON DELETE CASCADE;
