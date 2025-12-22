package com.thomas.espdoorbell.doorbell.core.exception

/**
 * Base sealed class for all domain-specific exceptions.
 * Provides semantic error handling with proper HTTP status mapping.
 */
sealed class DomainException(message: String): RuntimeException(message)

// ============ Not Found Exceptions ============

/**
 * Base exception for entity not found errors.
 */
open class EntityNotFoundException(
    entityType: String,
    entityId: Any
): DomainException("$entityType with id '$entityId' not found")

class DeviceNotFoundException(id: Any): EntityNotFoundException("Device", id)
class UserNotFoundException(id: Any): EntityNotFoundException("User", id)
class EventNotFoundException(id: Any): EntityNotFoundException("Event", id)
class DeviceAccessNotFoundException(deviceId: Any, userId: Any): 
    EntityNotFoundException("Device access for user '$userId' on device", deviceId)

// ============ Conflict Exceptions ============

/**
 * Base exception for entity conflict errors (duplicates, constraint violations).
 */
open class EntityConflictException(message: String): DomainException(message)

class DeviceAlreadyExistsException(identifier: String):
    EntityConflictException("Device with identifier '$identifier' already exists")

class EmailAlreadyInUseException(email: String):
    EntityConflictException("Email '$email' is already in use")

class UsernameAlreadyInUseException(username: String):
    EntityConflictException("Username '$username' is already in use")

class UserAlreadyHasAccessException(deviceId: Any, userId: Any):
    EntityConflictException("User '$userId' already has access to device '$deviceId'")

// ============ Invalid Operation Exceptions ============

/**
 * Exception for business rule violations or invalid state transitions.
 */
open class InvalidOperationException(message: String): DomainException(message)

class StreamAlreadyActiveException(deviceId: Any):
    InvalidOperationException("Stream for device '$deviceId' is already active")

class DeviceOfflineException(deviceId: Any):
    InvalidOperationException("Device '$deviceId' is offline")

// ============ Rate Limit Exceptions ============

/**
 * Exception thrown when a client exceeds the rate limit.
 */
class RateLimitExceededException(val retryAfterSeconds: Long):
    DomainException("Rate limit exceeded. Please retry after $retryAfterSeconds seconds")
