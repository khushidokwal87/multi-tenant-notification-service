ALTER TABLE users ALTER COLUMN tenant_id DROP NOT NULL;

-- The existing (tenant_id, username)/(tenant_id, email) unique constraints don't cover
-- platform admins (tenant_id IS NULL) since Postgres treats NULLs as distinct in a unique
-- constraint. Enforce global uniqueness for those rows with partial indexes instead.
CREATE UNIQUE INDEX uk_users_global_username ON users (username) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX uk_users_global_email ON users (email) WHERE tenant_id IS NULL;
