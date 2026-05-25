-- ============================================================
-- V1: services table — PostgreSQL metadata store
-- ============================================================

CREATE TABLE IF NOT EXISTS services (
    id              SERIAL          PRIMARY KEY,
    name            VARCHAR(64)     NOT NULL UNIQUE,
    display_name    VARCHAR(128)    DEFAULT '',
    description     VARCHAR(512)    DEFAULT '',
    language        VARCHAR(16)     DEFAULT 'other',
    repository_url  VARCHAR(256)    DEFAULT '',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT chk_services_name CHECK (name ~ '^[a-z][a-z0-9-]{2,63}$'),
    CONSTRAINT chk_services_language CHECK (language IN ('java', 'python', 'nodejs', 'go', 'other'))
);

COMMENT ON TABLE services IS 'Service registry. name is unique key, auto-discovered by Vector or manually registered';

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER trg_services_updated_at
    BEFORE UPDATE ON services
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
