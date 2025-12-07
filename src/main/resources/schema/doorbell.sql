-- Smart Doorbell Database Schema - SIMPLIFIED VERSION
-- Optimized for ESP32 doorbell with WebSocket streaming
--
-- DATABASE: doorbell

-- Terminate existing connections
SELECT pg_terminate_backend(pg_stat_activity.pid)
FROM pg_stat_activity
WHERE pg_stat_activity.datname = 'doorbell'
  AND pid <> pg_backend_pid();

SELECT 'CREATE DATABASE doorbell'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'doorbell')\gexec

\c doorbell

ALTER DATABASE doorbell SET timezone TO 'Asia/Ho_Chi_Minh';
SELECT pg_reload_conf();

-- ============================================================================
-- ENUM TYPES (Simplified)
-- ============================================================================

CREATE TYPE event_type_enum AS ENUM (
    'DOORBELL_RING',
    'MOTION_DETECTED',
    'LIVE_VIEW'
);

CREATE TYPE response_type_enum AS ENUM (
    'ANSWERED',
    'MISSED',
    'DECLINED',
    'PENDING'
);

CREATE TYPE stream_status_enum AS ENUM (
    'STREAMING',
    'PROCESSING',
    'COMPLETED',
    'FAILED'
);

CREATE TYPE user_role_enum AS ENUM (
    'OWNER',
    'MEMBER'
);

CREATE TYPE granted_status_enum AS ENUM (
    'GRANTED',
    'REVOKED',
    'EXPIRED'
);

-- ============================================================================
-- USER TABLES
-- ============================================================================

CREATE TABLE user_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    is_email_verified BOOLEAN DEFAULT FALSE,
    last_login TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT username_format_chk CHECK (
        username IS NULL OR username ~ '^[A-Za-z0-9._-]{3,50}$'
    ),
    CONSTRAINT email_format_chk CHECK (
        email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'
    )
);

CREATE TABLE user_profiles (
    id UUID PRIMARY KEY REFERENCES user_credentials(id) ON DELETE CASCADE,
    full_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(15) NOT NULL UNIQUE,
    notification_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT phone_num_format_chk CHECK (
        phone_number ~ '^0[1-9][0-9]+$' OR phone_number ~ '^\+84[1-9][0-9]+$'
    )
);

-- Phone normalization trigger
CREATE OR REPLACE FUNCTION normalize_user_profile_phone()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.phone_number LIKE '0%' THEN
        NEW.phone_number := '+84' || substring(NEW.phone_number FROM 2);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER user_profiles_phone_normalization
    BEFORE INSERT OR UPDATE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION normalize_user_profile_phone();

-- ============================================================================
-- DEVICES
-- ============================================================================

CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    location VARCHAR(255),
    model VARCHAR(100),
    firmware_version VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    battery_level INTEGER NOT NULL DEFAULT 100 CHECK (battery_level >= 0 AND battery_level <= 100),
    signal_strength INTEGER CHECK (signal_strength >= -100 AND signal_strength <= 0),
    last_online TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- USER DEVICE ACCESS (RBAC)
-- ============================================================================

CREATE TABLE user_device_access (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES user_credentials(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    role user_role_enum NOT NULL DEFAULT 'MEMBER',
    granted_status granted_status_enum NOT NULL DEFAULT 'GRANTED',
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES user_credentials(id) ON DELETE SET NULL
);

-- ============================================================================
-- EVENTS (Merged: events + streams)
-- ============================================================================

CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    
    -- Event info
    event_timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_type event_type_enum NOT NULL DEFAULT 'DOORBELL_RING',
    
    -- Response tracking
    response_type response_type_enum NOT NULL DEFAULT 'PENDING',
    response_timestamp TIMESTAMPTZ,
    responded_by UUID REFERENCES user_credentials(id) ON DELETE SET NULL,
    
    -- Stream info (merged from event_streams)
    stream_status stream_status_enum DEFAULT 'STREAMING',
    stream_started_at TIMESTAMPTZ,
    stream_ended_at TIMESTAMPTZ,
    duration_seconds INTEGER CHECK (duration_seconds >= 0),
    
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT check_response_timestamp CHECK (
        response_timestamp IS NULL OR response_timestamp >= event_timestamp
    ),
    CONSTRAINT check_stream_timestamps CHECK (
        stream_ended_at IS NULL OR stream_ended_at > stream_started_at
    )
);

-- ============================================================================
-- INDEXES
-- ============================================================================

CREATE INDEX idx_user_credentials_email ON user_credentials(email);
CREATE INDEX idx_user_credentials_username ON user_credentials(username) WHERE username IS NOT NULL;
CREATE INDEX idx_user_credentials_active ON user_credentials(is_active) WHERE is_active = TRUE;

CREATE INDEX idx_devices_active ON devices(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_devices_device_id ON devices(device_id);

CREATE INDEX idx_user_device_access_user ON user_device_access(user_id, role);
CREATE INDEX idx_user_device_access_device ON user_device_access(device_id, role);

CREATE INDEX idx_events_device_timestamp ON events(device_id, event_timestamp DESC);
CREATE INDEX idx_events_timestamp ON events(event_timestamp DESC);
CREATE INDEX idx_events_stream_status ON events(stream_status) WHERE stream_status IN ('STREAMING', 'PROCESSING');

-- ============================================================================
-- TRIGGERS
-- ============================================================================

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER user_credentials_updated_at BEFORE UPDATE ON user_credentials
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER user_profiles_updated_at BEFORE UPDATE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER devices_updated_at BEFORE UPDATE ON devices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER events_updated_at BEFORE UPDATE ON events
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- ============================================================================
-- SAMPLE DATA
-- ============================================================================

INSERT INTO devices (device_id, name, location, model, firmware_version)
VALUES
    ('DB001', 'Front Door', 'Main Entrance', 'SmartBell Pro', '2.4.1'),
    ('DB002', 'Back Door', 'Garden Entry', 'SmartBell Lite', '2.3.8')
ON CONFLICT (device_id) DO NOTHING;
