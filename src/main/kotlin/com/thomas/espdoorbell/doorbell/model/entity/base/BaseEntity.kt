package com.thomas.espdoorbell.doorbell.model.entity.base

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Column
import java.time.OffsetDateTime
import java.util.UUID

@Suppress("unused")
abstract class BaseEntity(
    @Id
    @Column("id")
    open val id: UUID = UUID.randomUUID(),

    @LastModifiedDate
    @Column("updated_at")
    val updatedAt: OffsetDateTime? = null,

    @CreatedDate
    @Column("created_at")
    val createdAt: OffsetDateTime? = null
) {
    abstract fun validate()
}