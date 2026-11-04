-- BookEasy Phase 11: indexes for the background jobs (payment expiry, reminders) found while reviewing query plans.
CREATE INDEX idx_appointments_status_created ON appointments (status, created_at);
CREATE INDEX idx_appointments_status_starts ON appointments (status, starts_at);
CREATE INDEX idx_refunds_status ON refunds (status, attempts);
