CREATE TABLE tenants (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    status     VARCHAR(50)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL REFERENCES tenants (id),
    username   VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(50)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_tenant_username UNIQUE (tenant_id, username),
    CONSTRAINT uk_users_tenant_email UNIQUE (tenant_id, email)
);

CREATE TABLE templates (
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL REFERENCES tenants (id),
    name       VARCHAR(255) NOT NULL,
    channel    VARCHAR(50)  NOT NULL,
    subject    VARCHAR(255),
    body       TEXT         NOT NULL,
    active     BOOLEAN      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_templates_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE channel_configs (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               BIGINT      NOT NULL REFERENCES tenants (id),
    channel                 VARCHAR(50) NOT NULL,
    enabled                 BOOLEAN     NOT NULL,
    rate_limit_per_minute   INTEGER     NOT NULL,
    max_retry               INTEGER     NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_channel_configs_tenant_channel UNIQUE (tenant_id, channel)
);

CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL REFERENCES tenants (id),
    template_id     BIGINT       REFERENCES templates (id),
    channel         VARCHAR(50)  NOT NULL,
    recipient       VARCHAR(255) NOT NULL,
    subject         VARCHAR(255),
    message         TEXT         NOT NULL,
    status          VARCHAR(50)  NOT NULL,
    scheduled_time  TIMESTAMPTZ,
    sent_time       TIMESTAMPTZ,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_notifications_tenant_idempotency_key UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX idx_users_tenant_id ON users (tenant_id);
CREATE INDEX idx_templates_tenant_id ON templates (tenant_id);
CREATE INDEX idx_channel_configs_tenant_id ON channel_configs (tenant_id);
CREATE INDEX idx_notifications_tenant_id ON notifications (tenant_id);
CREATE INDEX idx_notifications_tenant_status ON notifications (tenant_id, status);
CREATE INDEX idx_notifications_scheduled_time ON notifications (scheduled_time);
