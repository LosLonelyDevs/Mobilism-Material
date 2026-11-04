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
