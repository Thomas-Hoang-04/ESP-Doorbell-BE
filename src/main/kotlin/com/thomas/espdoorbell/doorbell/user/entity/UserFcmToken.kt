package com.thomas.espdoorbell.doorbell.user.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

@Table(name = "user_fcm_tokens")
class UserFcmToken(
    @Id
    @Column("id")
    val id: UUID? = null,

    @Column("user_id")
    val userId: UUID,

    @Column("token")
    val token: String,

    @LastModifiedDate
    @Column("last_updated_at")
    @Suppress("unused")
    val lastUpdatedAt: OffsetDateTime? = null
)
