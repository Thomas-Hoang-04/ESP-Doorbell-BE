package com.thomas.espdoorbell.doorbell.model.entity.base

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.OffsetDateTime
import java.util.UUID

@Suppress("unused")
@MappedSuperclass
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false)
    private lateinit var _id: UUID

    @UpdateTimestamp
    @Column(name = "updated_at")
    private lateinit var _updatedAt: OffsetDateTime

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private lateinit var _createdAt: OffsetDateTime

    val id: UUID
        get() = _id
    val createdAt: OffsetDateTime
        get() = _createdAt
    val updatedAt: OffsetDateTime
        get() = _updatedAt
}