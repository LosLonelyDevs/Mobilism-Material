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
