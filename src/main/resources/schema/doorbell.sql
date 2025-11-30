-- Smart Doorbell Database Schema - NORMALIZED VERSION
-- Segregated tables for better organization and JPA inheritance support
-- All timestamps stored in UTC (TIMESTAMPTZ)
--
-- DATABASE: doorbell
--
-- DEPLOYMENT:
--   Option 1 (Recommended):
--     createdb doorbell
--     psql -d doorbell -f schema.sql
--
--   Option 2 (All-in-one):
--     psql -f schema.sql
--
-- This script will create the 'doorbell' database if it doesn't exist,
-- then create all tables within that database.
--
-- ============================================================================
-- AUTHENTICATION ARCHITECTURE
-- ============================================================================
--
-- The schema now uses LOCAL AUTHENTICATION exclusively.
--   • user_credentials stores login emails, optional usernames, and hashed passwords
--   • user_profiles stores personal/contact data plus phone normalization
-- The split mirrors the Bike Rental App approach but swaps phone usernames for email-first
-- identifiers while still supporting classic usernames.
--
-- VIEWS:
--   • users_complete: Join credentials + profiles for easy querying
--   • Use this view for most application queries
--
-- DOCUMENTATION:
--   See /doc folder for detailed guides:
--   • README.md - Quick start and overview
--   • doc/SCHEMA.md - Complete schema reference
--   • doc/AUTH.md - Authentication implementation guide
--   • doc/EVENTS.md - Event types and workflows
--
-- EXAMPLE QUERIES:
--   -- Find user by login email
--   SELECT * FROM users_complete WHERE login_email = 'owner@example.com';
--
--   -- Or by username
--   SELECT * FROM users_complete WHERE username = 'frontdoor_admin';
--
--   -- Get all active users
--   SELECT * FROM users_complete WHERE is_active = true;

-- ============================================================================
-- DATABASE CREATION
-- ============================================================================

-- Terminate existing connections to the doorbell database (if recreating)
SELECT pg_terminate_backend(pg_stat_activity.pid)
FROM pg_stat_activity
WHERE pg_stat_activity.datname = 'doorbell'
  AND pid <> pg_backend_pid();

-- Create the doorbell database if it doesn't exist
SELECT 'CREATE DATABASE doorbell'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'doorbell')\gexec

-- Connect to doorbell database
    \c doorbell

-- Update database timezone
ALTER DATABASE doorbell SET timezone TO 'Asia/Ho_Chi_Minh';
SELECT pg_reload_conf();

-- ============================================================================
-- ENUM TYPES
-- ============================================================================

-- Event types - simplified to match the actual workflow
CREATE TYPE event_type_enum AS ENUM (
    -- Camera activation events (creates both event_streams and event_media)
    'DOORBELL_RING',               -- Button pressed → camera activates → stream plus record
    'MOTION_DETECTED',             -- Motion detected → camera activates → stream plus record
    'LIVE_VIEW',                   -- User opened live view → stream plus optional record

    -- Media-only events (creates event_media only)
    'SNAPSHOT',                    -- Single image capture (no streaming)

    -- System events (core event only, no media)
    'SYSTEM_CHECK',                -- System check (health monitoring)
    'DEVICE_SETTINGS_UPDATE',      -- Device settings update
    'USER_SETTINGS_UPDATE'         -- User settings update
    'FIRMWARE_UPDATE'              -- Firmware update
);

CREATE TYPE response_type_enum AS ENUM ('ANSWERED', 'MISSED', 'DECLINED', 'AUTO_RESPONSE', 'SYSTEM_RESPONSE', 'PENDING');
CREATE TYPE notification_type_enum AS ENUM ('PUSH', 'EMAIL', 'SMS');
CREATE TYPE stream_status_enum AS ENUM ('STREAMING', 'PROCESSING', 'COMPLETED', 'FAILED');
CREATE TYPE user_role_enum AS ENUM ('OWNER', 'ADMIN', 'MEMBER', 'GUEST');
CREATE TYPE granted_status_enum AS ENUM ('GRANTED', 'REVOKED', 'EXPIRED');

-- ============================================================================
-- CORE TABLES
-- ============================================================================

-- ============================================================================
-- USER AUTHENTICATION - Local auth with Bike schema inspiration
-- ============================================================================

CREATE TABLE user_credentials (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Authentication (local email and optional username)
                                  username VARCHAR(50) UNIQUE,
                                  email VARCHAR(255) NOT NULL UNIQUE,
                                  password VARCHAR(255) NOT NULL,

    -- Account status
                                  is_active BOOLEAN DEFAULT TRUE,
                                  is_email_verified BOOLEAN DEFAULT FALSE,
                                  last_login TIMESTAMPTZ,

                                  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
                                  CONSTRAINT username_format_chk
                                      CHECK (
                                          username IS NULL
                                              OR username ~ '^[A-Za-z0-9._-]{3,50}$'
),
                                 CONSTRAINT email_format_chk
                                     CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);

COMMENT ON TABLE user_credentials IS 'User authentication - local only with email logins + optional usernames';
COMMENT ON COLUMN user_credentials.username IS 'Optional unique handle (alphanumeric plus dot, underscore, dash)';
COMMENT ON COLUMN user_credentials.email IS 'Primary login email (case-insensitive compare recommended)';
COMMENT ON COLUMN user_credentials.password IS 'Hashed password for local auth (use bcrypt/argon2)';

-- ============================================================================
-- USER PROFILES - User details and preferences
-- ============================================================================

CREATE TABLE user_profiles (
                               id UUID PRIMARY KEY REFERENCES user_credentials(id) ON DELETE CASCADE,

    -- Personal information
                               full_name VARCHAR(255) NOT NULL,
                               phone_number VARCHAR(15) NOT NULL UNIQUE,
                               dob DATE,
                               timezone VARCHAR(64) DEFAULT 'Asia/Ho_Chi_Minh',

    -- Notification settings
                               notification_enabled BOOLEAN DEFAULT TRUE,
                               quiet_hours_start TIME WITH TIME ZONE,
                               quiet_hours_end TIME WITH TIME ZONE,

                               created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
                               CONSTRAINT phone_num_format_chk
                                   CHECK (
                                       phone_number ~ '^0[1-9][0-9]+$'
                                       OR phone_number ~ '^\+84[1-9][0-9]+$'
)
    );

COMMENT ON TABLE user_profiles IS 'User profile information, normalized phone numbers, and notification preferences';
COMMENT ON COLUMN user_profiles.full_name IS 'Name shown in UI and notifications';

-- Normalize local phone inputs to +84 for consistency (Bike Rental schema inspiration)
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

-- Doorbell devices
CREATE TABLE devices (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         device_id VARCHAR(100) UNIQUE NOT NULL,
                         name VARCHAR(255) NOT NULL,
                         location VARCHAR(255),
                         model VARCHAR(100),
                         firmware_version VARCHAR(50),
                         is_active BOOLEAN DEFAULT TRUE,

    -- Health metrics
                         battery_level INTEGER NOT NULL DEFAULT 100 CHECK (battery_level >= 0 AND battery_level <= 100),
                         signal_strength INTEGER CHECK (signal_strength >= -100 AND signal_strength <= 0),
                         last_online TIMESTAMPTZ,

                         created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE devices IS 'Physical doorbell devices';

-- User device access control (RBAC)
CREATE TABLE user_device_access (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    user_id UUID NOT NULL REFERENCES user_credentials(id) ON DELETE CASCADE,
                                    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
                                    role user_role_enum NOT NULL DEFAULT 'MEMBER',
                                    granted_status granted_status_enum NOT NULL DEFAULT 'GRANTED',
                                    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                                    updated_by UUID REFERENCES user_credentials(id) ON DELETE SET NULL
);

COMMENT ON TABLE user_device_access IS 'Role-based access control for device permissions';
COMMENT ON COLUMN user_device_access.role IS 'OWNER: full control, ADMIN: manage settings, MEMBER: view & respond';

-- Trigger function to expire access when updated_by user is deleted
CREATE OR REPLACE FUNCTION expire_access_on_updater_deleted()
RETURNS TRIGGER AS $$
BEGIN
    -- If updated_by changed from a value to NULL, mark as EXPIRED
    IF OLD.updated_by IS NOT NULL AND NEW.updated_by IS NULL THEN
        NEW.granted_status = 'EXPIRED';
        RAISE NOTICE 'Access expired for user % on device % (updater deleted)',
            NEW.user_id, NEW.device_id;
END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER user_device_access_expire_on_updater_deleted
    BEFORE UPDATE ON user_device_access
    FOR EACH ROW
    WHEN (OLD.updated_by IS DISTINCT FROM NEW.updated_by)
    EXECUTE FUNCTION expire_access_on_updater_deleted();

COMMENT ON FUNCTION expire_access_on_updater_deleted() IS
    'Automatically sets granted_status to EXPIRED when updated_by user is deleted (set to NULL)';

-- ============================================================================
-- EVENTS - Base table (segregated, cleaner!)
-- ============================================================================

CREATE TABLE events (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
                        event_timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        event_type event_type_enum NOT NULL DEFAULT 'DOORBELL_RING',

    -- Response tracking
                        response_type response_type_enum NOT NULL DEFAULT 'PENDING',
                        response_timestamp TIMESTAMPTZ,
                        responded_by UUID REFERENCES user_credentials(id) ON DELETE SET NULL,

                        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,

                        CONSTRAINT check_response_timestamp
                            CHECK (response_timestamp IS NULL OR response_timestamp >= event_timestamp)
);

COMMENT ON TABLE events IS 'Core doorbell events - base information only';

-- ============================================================================
-- EVENT_STREAMS - Streaming-specific data (1-to-1 with events)
-- ============================================================================

CREATE TABLE event_streams (
                               id UUID PRIMARY KEY REFERENCES events(id) ON DELETE CASCADE,

    -- Streaming metadata
                               stream_status stream_status_enum DEFAULT 'STREAMING',
                               stream_started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               stream_ended_at TIMESTAMPTZ,
                               stream_error_message TEXT,
                               stream_retry_count INTEGER NOT NULL DEFAULT 0 CHECK (stream_retry_count >= 0),

    -- HLS live streaming
                               hls_playlist_url VARCHAR(500),

    -- Raw stream storage (temporary, for post-processing)
                               raw_video_path VARCHAR(500),
                               raw_audio_path VARCHAR(500),

                               created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT check_stream_timestamps
                                   CHECK (stream_ended_at IS NULL OR stream_ended_at > stream_started_at)
);

COMMENT ON TABLE event_streams IS 'Streaming data for events - HLS, status, raw files';
COMMENT ON COLUMN event_streams.hls_playlist_url IS 'HLS manifest URL for live streaming';
COMMENT ON COLUMN event_streams.raw_video_path IS 'Temporary storage path, deleted after processing';

-- ============================================================================
-- EVENT_MEDIA - Final processed media (1-to-1 with events)
-- ============================================================================

CREATE TABLE event_media (
                             id UUID PRIMARY KEY REFERENCES events(id) ON DELETE CASCADE,

    -- Final processed media URLs
                             video_url VARCHAR(500),
                             thumbnail_url VARCHAR(500),

    -- Media metadata
                             duration_seconds INTEGER CHECK (duration_seconds >= 0),
                             video_codec VARCHAR(50),
                             audio_codec VARCHAR(50),
                             resolution VARCHAR(20),
                             file_size_bytes BIGINT CHECK (file_size_bytes >= 0),

                             created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE event_media IS 'Final processed media files and metadata';

-- ============================================================================
-- NOTIFICATIONS
-- ============================================================================

CREATE TABLE notifications (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
                               notification_type notification_type_enum NOT NULL,
                               recipient VARCHAR(255) NOT NULL,
                               recipient_user_id UUID REFERENCES user_credentials(id) ON DELETE SET NULL,
                               sent_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                               success BOOLEAN DEFAULT FALSE,
                               error_message TEXT
);

COMMENT ON TABLE notifications IS 'Notification delivery tracking';
COMMENT ON COLUMN notifications.recipient_user_id IS 'Optional FK to the user who received the notification; stores both FK and raw recipient address';

-- ============================================================================
-- INDEXES
-- ============================================================================

-- User Credentials (Authentication)
CREATE INDEX idx_user_credentials_email ON user_credentials(email);
CREATE INDEX idx_user_credentials_username ON user_credentials(username) WHERE username IS NOT NULL;
CREATE INDEX idx_user_credentials_last_login ON user_credentials(last_login DESC) WHERE last_login IS NOT NULL;
CREATE INDEX idx_user_credentials_active ON user_credentials(is_active) WHERE is_active = TRUE;

-- Devices
CREATE INDEX idx_devices_active ON devices(is_active) WHERE is_active = TRUE;

-- User Device Access (RBAC)
CREATE INDEX idx_user_device_access_user ON user_device_access(user_id, role);
CREATE INDEX idx_user_device_access_device ON user_device_access(device_id, role);

-- Events (base table)
CREATE INDEX idx_events_device_timestamp ON events(device_id, event_timestamp DESC);
CREATE INDEX idx_events_timestamp ON events(event_timestamp DESC);
CREATE INDEX idx_events_unanswered ON events(device_id, event_timestamp DESC)
    WHERE response_type IS NULL OR response_type = 'MISSED';

-- Event Streams
CREATE INDEX idx_event_streams_status ON event_streams(stream_status);
CREATE INDEX idx_event_streams_streaming ON event_streams(stream_started_at)
    WHERE stream_status IN ('STREAMING', 'PROCESSING');

-- Event Media
CREATE INDEX idx_event_media_video_url ON event_media(video_url) WHERE video_url IS NOT NULL;

-- Notifications
CREATE INDEX idx_notifications_event_id ON notifications(event_id);
CREATE INDEX idx_notifications_recipient ON notifications(recipient, sent_at DESC);
CREATE INDEX idx_notifications_recipient_user ON notifications(recipient_user_id);

-- ============================================================================
-- TRIGGERS
-- ============================================================================

-- Auto-update updated_at timestamp
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

CREATE TRIGGER event_streams_updated_at BEFORE UPDATE ON event_streams
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER event_media_updated_at BEFORE UPDATE ON event_media
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- ============================================================================
-- VIEWS - Convenience views for common queries
-- ============================================================================

-- Complete event view (joins all related tables)
CREATE OR REPLACE VIEW events_complete AS
SELECT
    e.*,
    es.stream_status,
    es.stream_started_at,
    es.stream_ended_at,
    es.stream_error_message,
    es.stream_retry_count,
    es.hls_playlist_url,
    es.raw_video_path,
    es.raw_audio_path,
    em.video_url,
    em.thumbnail_url,
    em.duration_seconds,
    em.video_codec,
    em.audio_codec,
    em.resolution,
    em.file_size_bytes,
    d.name as device_name,
    d.location as device_location,
    up.full_name as responded_by_name
FROM events e
         LEFT JOIN event_streams es ON e.id = es.id
         LEFT JOIN event_media em ON e.id = em.id
         LEFT JOIN devices d ON e.device_id = d.id
         LEFT JOIN user_credentials uc ON e.responded_by = uc.id
         LEFT JOIN user_profiles up ON uc.id = up.id;

COMMENT ON VIEW events_complete IS 'Denormalized view of events with all related data';

-- Active streams view
CREATE OR REPLACE VIEW active_streams AS
SELECT
    e.id,
    e.event_timestamp,
    d.name as device_name,
    d.location,
    es.stream_status,
    es.stream_started_at,
    es.hls_playlist_url,
    EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - es.stream_started_at)) as stream_duration_seconds
FROM events e
         JOIN event_streams es ON e.id = es.id
         JOIN devices d ON e.device_id = d.id
WHERE es.stream_status IN ('STREAMING', 'PROCESSING')
ORDER BY es.stream_started_at DESC;

COMMENT ON VIEW active_streams IS 'Currently active streams';

-- User complete view (for easier queries)
CREATE OR REPLACE VIEW users_complete AS
SELECT
    uc.id,
    uc.email as login_email,
    uc.username,
    uc.is_active,
    uc.is_email_verified,
    uc.last_login,
    uc.created_at as credential_created_at,
    up.full_name,
    up.phone_number,
    up.dob,
    up.timezone as preferred_timezone,
    up.notification_enabled,
    up.quiet_hours_start,
    up.quiet_hours_end,
    up.created_at as profile_created_at
FROM user_credentials uc
         LEFT JOIN user_profiles up ON uc.id = up.id;

COMMENT ON VIEW users_complete IS 'Denormalized view of users with credentials and profile data';

-- ============================================================================
-- SAMPLE DATA
-- ============================================================================

INSERT INTO devices (device_id, name, location, model, firmware_version)
VALUES
    ('DB001', 'Front Door', 'Main Entrance', 'SmartBell Pro', '2.4.1'),
    ('DB002', 'Back Door', 'Garden Entry', 'SmartBell Lite', '2.3.8')
    ON CONFLICT (device_id) DO NOTHING;


-- ============================================================================
-- MAINTENANCE FUNCTIONS
-- ============================================================================

-- Function to clean up old raw stream files (automatic clean-up)
CREATE OR REPLACE FUNCTION cleanup_old_raw_files()
    RETURNS TABLE(rows_cleaned INTEGER, space_freed_estimate TEXT) AS $$
DECLARE
rows_updated INTEGER;
BEGIN
    -- Remove raw file paths for completed streams older than 30 days
UPDATE event_streams
SET raw_video_path = NULL,
    raw_audio_path = NULL
WHERE stream_status = 'COMPLETED'
  AND stream_ended_at < NOW() - INTERVAL '30 days'
  AND (raw_video_path IS NOT NULL OR raw_audio_path IS NOT NULL);

GET DIAGNOSTICS rows_updated = ROW_COUNT;

RETURN QUERY SELECT
        rows_updated,
        CASE
            WHEN rows_updated > 0 THEN rows_updated || ' raw file references cleaned'
            ELSE 'No old raw files to clean'
END;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_raw_files() IS 'Removes raw file references from completed streams older than 30 days';

-- Function to clean up failed streams (for retry or manual clean-up)
CREATE OR REPLACE FUNCTION cleanup_failed_streams(days_old INTEGER DEFAULT 7)
    RETURNS TABLE(failed_streams INTEGER) AS $$
DECLARE
rows_updated INTEGER;
BEGIN
    -- Mark very old failed streams for clean-up
UPDATE event_streams
SET raw_video_path = NULL,
    raw_audio_path = NULL
WHERE stream_status = 'FAILED'
  AND stream_ended_at < NOW() - (days_old || ' days')::INTERVAL
      AND (raw_video_path IS NOT NULL OR raw_audio_path IS NOT NULL);

GET DIAGNOSTICS rows_updated = ROW_COUNT;

RETURN QUERY SELECT rows_updated;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_failed_streams(INTEGER) IS 'Removes raw file references from failed streams older than specified days';

-- ============================================================================
-- SCHEDULED CLEANUP (Using pg_cron extension)
-- ============================================================================

-- Uncomment below if you have pg_cron extension installed,
-- This will automatically run clean-up daily at 3 AM

-- CREATE EXTENSION IF NOT EXISTS pg_cron;
--
-- -- Schedule automatic clean-up (runs daily at 3 AM)
-- SELECT cron.schedule(
--     'cleanup-old-raw-files',
--     '0 3 * * *',  -- Every day at 3:00 AM
--     'SELECT cleanup_old_raw_files();'
-- );
--
-- -- Optional: cleanup failed streams weekly
-- SELECT cron.schedule(
--     'cleanup-failed-streams',
--     '0 4 * * 0',  -- Every Sunday at 4:00 AM
--     'SELECT cleanup_failed_streams(30);'  -- Cleanup failed streams older than 30 days
-- );

-- ============================================================================
-- MANUAL CLEANUP USAGE
-- ============================================================================

-- To manually run clean-up:
-- SELECT * FROM cleanup_old_raw_files();
-- SELECT * FROM cleanup_failed_streams(7);  -- Cleanup failed streams older than 7 days

