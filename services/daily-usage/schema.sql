CREATE TABLE IF NOT EXISTS usage_receipts (
    day date NOT NULL,
    ping_id uuid NOT NULL,
    PRIMARY KEY (day, ping_id)
);

CREATE TABLE IF NOT EXISTS daily_usage (
    day date NOT NULL,
    app_version varchar(50) NOT NULL,
    version_code integer NOT NULL CHECK (version_code > 0),
    android_api integer NOT NULL CHECK (android_api BETWEEN 26 AND 100),
    manufacturer varchar(80) NOT NULL,
    model varchar(120) NOT NULL,
    devices bigint NOT NULL CHECK (devices > 0),
    PRIMARY KEY (day, app_version, version_code, android_api, manufacturer, model)
);

CREATE TABLE IF NOT EXISTS request_receipts (
    day date NOT NULL,
    ping_id uuid NOT NULL,
    PRIMARY KEY (day, ping_id)
);

CREATE TABLE IF NOT EXISTS daily_requests (
    day date NOT NULL,
    app_version varchar(50) NOT NULL,
    version_code integer NOT NULL CHECK (version_code > 0),
    endpoint varchar(32) NOT NULL,
    method varchar(7) NOT NULL,
    successes bigint NOT NULL CHECK (successes >= 0),
    redirects bigint NOT NULL CHECK (redirects >= 0),
    client_errors bigint NOT NULL CHECK (client_errors >= 0),
    rate_limited bigint NOT NULL CHECK (rate_limited >= 0),
    server_errors bigint NOT NULL CHECK (server_errors >= 0),
    network_errors bigint NOT NULL CHECK (network_errors >= 0),
    duration_ms bigint NOT NULL CHECK (duration_ms >= 0),
    PRIMARY KEY (day, app_version, version_code, endpoint, method)
);

CREATE TABLE IF NOT EXISTS daily_cache_usage (
    day date NOT NULL,
    app_version varchar(50) NOT NULL,
    version_code integer NOT NULL CHECK (version_code > 0),
    cache varchar(24) NOT NULL,
    hits bigint NOT NULL CHECK (hits >= 0),
    misses bigint NOT NULL CHECK (misses >= 0),
    PRIMARY KEY (day, app_version, version_code, cache)
);

CREATE TABLE IF NOT EXISTS daily_followed_apps (
    day date NOT NULL,
    app_key varchar(200) NOT NULL,
    name varchar(200) NOT NULL,
    followers bigint NOT NULL CHECK (followers >= 0),
    misses bigint NOT NULL CHECK (misses >= 0),
    PRIMARY KEY (day, app_key)
);

-- Older rows recorded misses only. Keep those totals without guessing historical hits.
ALTER TABLE daily_followed_apps
    ADD COLUMN IF NOT EXISTS hits bigint NOT NULL DEFAULT 0 CHECK (hits >= 0),
    ADD COLUMN IF NOT EXISTS measured_misses bigint NOT NULL DEFAULT 0 CHECK (measured_misses >= 0);

ALTER TABLE daily_followed_apps
    ADD COLUMN IF NOT EXISTS other_lookups bigint NOT NULL DEFAULT 0 CHECK (other_lookups >= 0);

ALTER TABLE daily_followed_apps
    ADD COLUMN IF NOT EXISTS last_requested_at bigint CHECK (last_requested_at > 0),
    ADD COLUMN IF NOT EXISTS background_hits bigint NOT NULL DEFAULT 0 CHECK (background_hits >= 0),
    ADD COLUMN IF NOT EXISTS background_misses bigint NOT NULL DEFAULT 0 CHECK (background_misses >= 0),
    ADD COLUMN IF NOT EXISTS manual_hits bigint NOT NULL DEFAULT 0 CHECK (manual_hits >= 0),
    ADD COLUMN IF NOT EXISTS manual_misses bigint NOT NULL DEFAULT 0 CHECK (manual_misses >= 0);

CREATE TABLE IF NOT EXISTS follow_lookup_receipts (
    day date NOT NULL,
    receipt uuid NOT NULL,
    PRIMARY KEY (day, receipt)
);

ALTER TABLE daily_followed_apps
    ADD COLUMN IF NOT EXISTS last_client_version varchar(50);

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_constraint WHERE conrelid = 'daily_cache_usage'::regclass
        AND conname = 'daily_cache_usage_cache_names_v2') THEN
        ALTER TABLE daily_cache_usage DROP CONSTRAINT IF EXISTS daily_cache_usage_cache_check,
            ADD CONSTRAINT daily_cache_usage_cache_names_v2 CHECK (cache IN ('FollowingBackground',
                'FollowingManual', 'MemberIdentity', 'Search', 'Read', 'ReleaseFeed', 'Inbox', 'Images', 'ReleaseComments'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS admin_login_limits (
    key text PRIMARY KEY,
    failures integer NOT NULL,
    started_at timestamptz NOT NULL,
    blocked_until timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS admin_login_challenges (
    nonce text PRIMARY KEY,
    client text NOT NULL,
    expires_at timestamptz NOT NULL,
    attempts integer NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS admin_totp_counters (
    account text PRIMARY KEY,
    step bigint NOT NULL
);

CREATE TABLE IF NOT EXISTS app_control (
    id integer PRIMARY KEY CHECK (id = 1),
    revision integer NOT NULL CHECK (revision > 0),
    config jsonb NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS dashboard_users (
    id text PRIMARY KEY,
    username varchar(64) NOT NULL,
    password_hash text NOT NULL,
    totp_secret text NOT NULL,
    is_admin boolean NOT NULL DEFAULT false,
    pages text[] NOT NULL DEFAULT '{}',
    auth_version integer NOT NULL DEFAULT 1,
    CONSTRAINT dashboard_users_pages_v2 CHECK (pages <@ ARRAY['overview', 'app-usage', 'app-controls', 'following-cache']::text[]),
    CHECK (id <> 'owner' OR (is_admin AND pages @> ARRAY['overview', 'app-usage', 'app-controls']::text[]))
);
CREATE UNIQUE INDEX IF NOT EXISTS dashboard_users_username ON dashboard_users (lower(username));

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_constraint WHERE conrelid = 'dashboard_users'::regclass AND conname = 'dashboard_users_pages_v2') THEN
        ALTER TABLE dashboard_users DROP CONSTRAINT dashboard_users_pages_check,
            ADD CONSTRAINT dashboard_users_pages_v2 CHECK (pages <@ ARRAY['overview', 'app-usage', 'app-controls', 'following-cache']::text[]);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS dashboard_sessions (
    nonce text PRIMARY KEY,
    user_id text NOT NULL REFERENCES dashboard_users(id) ON DELETE CASCADE,
    auth_version integer NOT NULL,
    expires_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS dashboard_sessions_user ON dashboard_sessions (user_id);

ALTER TABLE admin_login_challenges ADD COLUMN IF NOT EXISTS user_id text REFERENCES dashboard_users(id) ON DELETE CASCADE;
ALTER TABLE admin_login_challenges ADD COLUMN IF NOT EXISTS auth_version integer;

CREATE TABLE IF NOT EXISTS app_write_windows (
    bucket text NOT NULL,
    window_start bigint NOT NULL,
    nonces uuid[] NOT NULL CHECK (cardinality(nonces) BETWEEN 1 AND 120),
    PRIMARY KEY (bucket, window_start)
);
CREATE INDEX IF NOT EXISTS app_write_windows_expiry ON app_write_windows(window_start);

CREATE TABLE IF NOT EXISTS latest_apps_cache (
    singleton boolean PRIMARY KEY CHECK (singleton),
    items jsonb NOT NULL,
    observed_at bigint NOT NULL DEFAULT 0
);
ALTER TABLE latest_apps_cache
    DROP CONSTRAINT IF EXISTS latest_apps_cache_items_check,
    ADD CONSTRAINT latest_apps_cache_items_check CHECK (jsonb_typeof(items) = 'array' AND jsonb_array_length(items) <= 2000);
ALTER TABLE latest_apps_cache ADD COLUMN IF NOT EXISTS category_top jsonb NOT NULL DEFAULT '[]'
    CHECK (jsonb_typeof(category_top) = 'array' AND jsonb_array_length(category_top) <= 500);
INSERT INTO latest_apps_cache(singleton, items) VALUES (true, '[]') ON CONFLICT DO NOTHING;
