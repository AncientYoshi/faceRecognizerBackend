CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    action VARCHAR(40) NOT NULL,
    entity_type VARCHAR(80),
    entity_id VARCHAR(100),
    details VARCHAR(1000),
    ip_address VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

CREATE TABLE system_settings (
    id UUID PRIMARY KEY,
    setting_key VARCHAR(80) NOT NULL UNIQUE,
    setting_value VARCHAR(500) NOT NULL,
    value_type VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    updated_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO system_settings (
    id, setting_key, setting_value, value_type, description, created_at, updated_at, version
) VALUES
    ('00000000-0000-0000-0000-000000000101', 'ATTENDANCE_THRESHOLD', '0.75', 'DECIMAL',
     'Minimum attendance rate used by administrative reporting', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000102', 'AI_SIMILARITY_THRESHOLD', '0.80', 'DECIMAL',
     'Minimum face similarity score expected from the AI service', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000103', 'CURRENT_SEMESTER', 'FIRST', 'STRING',
     'Current academic semester', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000104', 'CURRENT_ACADEMIC_YEAR', '2026-2027', 'STRING',
     'Current academic year', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
