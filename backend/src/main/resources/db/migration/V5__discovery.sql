-- BookEasy Phase 6: discovery. Nearby search pre-filters on a latitude/longitude bounding box, then ranks by
-- ST_Distance_Sphere on the generated POINT column. (MySQL can't build a geographic envelope for MBRContains, so the
-- box uses plain DECIMAL columns with this index.)
CREATE INDEX idx_branches_lat_lng ON organization_branches (latitude, longitude);
CREATE INDEX idx_services_name ON services (name);
