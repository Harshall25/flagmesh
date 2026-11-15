-- V1: FlagMesh initial schema.
-- Flyway runs this file exactly once, then stores its checksum.
-- Never edit it after it has run: put later changes in V2__..., V3__...
-- Schema "flags" itself is created by Flyway (spring.flyway.schemas: flags).

-- One row per flag: on/off state, rollout, allowlist and runtime config JSON.

CREATE TABLE flags.feature_flags (
                                     flag_key     VARCHAR(100) PRIMARY KEY,
                                     enabled      BOOLEAN      NOT NULL DEFAULT false,
                                     rollout_pct  INT          NOT NULL DEFAULT 0
                                         CONSTRAINT ck_feature_flags_rollout_pct
                                             CHECK (rollout_pct BETWEEN 0 AND 100),
                                     allow_users  JSONB        NOT NULL DEFAULT '[]'::jsonb
                 CONSTRAINT ck_feature_flags_allow_users_is_array
                 CHECK (jsonb_typeof(allow_users) = 'array'),
                                     payload      JSONB        NOT NULL DEFAULT '{}'::jsonb
                 CONSTRAINT ck_feature_flags_payload_is_object
                 CHECK (jsonb_typeof(payload) = 'object'),
                                     version      BIGINT       NOT NULL DEFAULT 1,   -- +1 on every change
                                     updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Immutable history: exact before/after JSON of every change.
CREATE TABLE flags.flag_audit_logs (
                                       id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                       flag_key    VARCHAR(100) NOT NULL
                                           CONSTRAINT fk_flag_audit_logs_feature_flags
                                               REFERENCES flags.feature_flags (flag_key),
                                       actor       TEXT         NOT NULL,
                                       old_state   JSONB,                              -- NULL when the flag was just created
                                       new_state   JSONB        NOT NULL,
                                       changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Serves "audit history of one flag, newest first".
CREATE INDEX ix_flag_audit_logs_flag_key_changed_at
    ON flags.flag_audit_logs (flag_key, changed_at DESC);

-- Seed flags. allow_users, version and updated_at use their defaults.
INSERT INTO flags.feature_flags (flag_key, enabled, rollout_pct, payload) VALUES
                                                                              ('observability.log-level', true,  100, '{"defaultLevel": "INFO", "debugLevel": "DEBUG", "targetUserId": "user_42"}'),
                                                                              ('payment.rate-limit',      true,  100, '{"requestsPerMinute": 30, "burst": 5}'),
                                                                              ('payment.kill-switch',     false,   0, '{"retryAfterSeconds": 60, "reason": "Scheduled maintenance"}'),
                                                                              ('payment.fraud-v2',        true,   25, '{"riskScoreThreshold": 0.75, "timeoutMs": 150}');