-- V3 — Add detection confidence and signal columns to microservices

ALTER TABLE microservices ADD COLUMN detection_confidence VARCHAR(20);
ALTER TABLE microservices ADD COLUMN detection_signal     VARCHAR(100);

