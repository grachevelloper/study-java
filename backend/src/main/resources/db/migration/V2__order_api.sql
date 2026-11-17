ALTER TABLE customers
  ADD COLUMN public_id uuid NOT NULL DEFAULT gen_random_uuid(),
  ADD CONSTRAINT uq_customer_public_id UNIQUE (public_id);

ALTER TABLE customer_orders
  ADD COLUMN public_id uuid NOT NULL DEFAULT gen_random_uuid(),
  ADD COLUMN external_id varchar(100),
  ADD COLUMN urgent boolean NOT NULL DEFAULT false,
  ADD COLUMN archived boolean NOT NULL DEFAULT false,
  ADD COLUMN updated_at timestamp,
  ADD COLUMN version bigint NOT NULL DEFAULT 0,
  ADD CONSTRAINT uq_order_public_id UNIQUE (public_id);

UPDATE customer_orders
SET external_id = 'ORDER-' || id,
    updated_at = greatest(created_at, paid_at, shipped_at, delivered_at, cancelled_at);

ALTER TABLE customer_orders
  ALTER COLUMN external_id SET DEFAULT ('ORDER-' || gen_random_uuid()),
  ALTER COLUMN external_id SET NOT NULL,
  ALTER COLUMN updated_at SET DEFAULT LOCALTIMESTAMP,
  ALTER COLUMN updated_at SET NOT NULL,
  ADD CONSTRAINT uq_order_external_id UNIQUE (external_id);

CREATE FUNCTION touch_legacy_order() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.version = OLD.version THEN
    NEW.version := OLD.version + 1;
    NEW.updated_at := greatest(LOCALTIMESTAMP, OLD.updated_at + interval '1 microsecond');
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER touch_legacy_order BEFORE UPDATE ON customer_orders
FOR EACH ROW EXECUTE FUNCTION touch_legacy_order();

CREATE INDEX idx_order_archived ON customer_orders (archived);
CREATE INDEX idx_order_urgent ON customer_orders (urgent);
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_order_external_search ON customer_orders USING gin (lower(external_id) gin_trgm_ops);
CREATE INDEX idx_order_note_search ON customer_orders USING gin (lower(note) gin_trgm_ops);
CREATE INDEX idx_customer_name_search ON customers USING gin (lower(full_name) gin_trgm_ops);
CREATE INDEX idx_product_name_search ON products USING gin (lower(name) gin_trgm_ops);

CREATE TABLE app_user (
                          id UUID PRIMARY KEY,
                          email VARCHAR(254) NOT NULL UNIQUE,
                          display_name VARCHAR(160) NOT NULL,
                          password_hash VARCHAR(100) NOT NULL,
                          enabled BOOLEAN NOT NULL DEFAULT TRUE,
                          password_change_required BOOLEAN NOT NULL DEFAULT TRUE,
                          failed_login_attempts INTEGER NOT NULL DEFAULT 0,
                          locked_until TIMESTAMPTZ,
                          auth_version BIGINT NOT NULL DEFAULT 0,
                          created_at TIMESTAMPTZ NOT NULL,
                          updated_at TIMESTAMPTZ NOT NULL,
                          version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE app_user_role (
                               user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
                               role VARCHAR(32) NOT NULL,
                               PRIMARY KEY (user_id, role)
);

CREATE TABLE refresh_session (
                                 id UUID PRIMARY KEY,
                                 user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
                                 auth_version BIGINT NOT NULL DEFAULT 0,
                                 token_hash VARCHAR(64) NOT NULL UNIQUE,
                                 expires_at TIMESTAMPTZ NOT NULL,
                                 revoked_at TIMESTAMPTZ,
                                 replaced_by UUID,
                                 user_agent VARCHAR(500),
                                 ip_address VARCHAR(64),
                                 created_at TIMESTAMPTZ NOT NULL,
                                 last_used_at TIMESTAMPTZ
);

CREATE INDEX idx_refresh_user
    ON refresh_session(user_id);

CREATE INDEX idx_refresh_expires
    ON refresh_session(expires_at);


CREATE TABLE order_tag (
  order_id bigint NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
  tag varchar(50) NOT NULL,
  PRIMARY KEY (order_id, tag)
);
CREATE INDEX idx_order_tag_value ON order_tag(tag);

CREATE TABLE order_note (
  id uuid PRIMARY KEY,
  order_id bigint NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
  author_id uuid NOT NULL REFERENCES app_user(id),
  body varchar(4000) NOT NULL,
  pinned boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_order_note_history ON order_note(order_id, pinned DESC, created_at DESC);

CREATE TABLE audit_event (
                             id UUID PRIMARY KEY,
                             actor_id UUID,
                             actor_email VARCHAR(254),
                             event_type VARCHAR(80) NOT NULL,
                             entity_type VARCHAR(80),
                             entity_id UUID,
                             summary VARCHAR(1000) NOT NULL,
                             correlation_id VARCHAR(100),
                             ip_address VARCHAR(64),
                             user_agent VARCHAR(500),
                             occurred_at TIMESTAMPTZ NOT NULL,
                             details_json TEXT
);

CREATE INDEX idx_audit_occurred
    ON audit_event(occurred_at DESC);

CREATE INDEX idx_audit_actor
    ON audit_event(actor_email);

CREATE INDEX idx_audit_entity
    ON audit_event(entity_type, entity_id);
