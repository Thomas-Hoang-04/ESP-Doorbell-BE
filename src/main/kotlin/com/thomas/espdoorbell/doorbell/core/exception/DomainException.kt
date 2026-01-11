package com.thomas.espdoorbell.doorbell.core.exception

sealed class DomainException(message: String) : RuntimeException(message) {

    open class EntityNotFound(
        entityType: String,
        idType: String,
        idValue: String,
    ) : DomainException("$entityType with $idType '$idValue' not found") {
        class Device(type: String, value: String) : EntityNotFound("Device", type, value)
        class User(type: String, value: String) : EntityNotFound("User", type, value)
        class Event(type: String, value: String) : EntityNotFound("Event", type, value)
    }

    open class EntityConflict(message: String) : DomainException(message) {
        class DeviceAlreadyExists(identifier: String) :
            EntityConflict("Device with identifier '$identifier' already exists")
        class EmailAlreadyInUse(email: String) :
            EntityConflict("Email '$email' is already in use")
        class UsernameAlreadyInUse(username: String) :
            EntityConflict("Username '$username' is already in use")
        class UserAlreadyHasAccess(deviceId: Any, userId: Any) :
            EntityConflict("User '$userId' already has access to device '$deviceId'")
    }

    open class AccessDenied(userId: String, deviceId: String)
        : DomainException("User '$userId' does not have access to device '$deviceId'")

    @Suppress("unused")
    open class InvalidOperation(message: String) : DomainException(message) {
        class StreamAlreadyActive(deviceId: Any) :
            InvalidOperation("Stream for device '$deviceId' is already active")
        class DeviceOffline(deviceId: Any) :
            InvalidOperation("Device '$deviceId' is offline")
    }
}
