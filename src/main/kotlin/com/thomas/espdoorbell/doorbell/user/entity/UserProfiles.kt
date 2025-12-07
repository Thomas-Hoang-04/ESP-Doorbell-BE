package com.thomas.espdoorbell.doorbell.user.entity

import com.thomas.espdoorbell.doorbell.shared.entity.BaseEntity
import com.thomas.espdoorbell.doorbell.user.dto.UserProfileDto
import org.springframework.data.annotation.Transient
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.util.*

@Table("user_profiles")
class UserProfiles(
    @Transient
    private val user: UUID,

    @Column("full_name")
    private val storedFullName: String,

    @Column("phone_number")
    private val phoneNumber: String,

    @Column("notification_enabled")
    private val notificationEnabled: Boolean = true
): BaseEntity(id = user) {

    init { validate() }

    override fun validate() {
        val localPattern = Regex("^0[1-9][0-9]+$")
        val e164Pattern = Regex("^\\+[1-9]\\d{1,14}$")
        require(phoneNumber.matches(localPattern) || phoneNumber.matches(e164Pattern)) {
            "Phone number must be in valid E.164 format or normalized local format"
        }
    }

    val fullName: String
        get() = storedFullName

    val normalizedPhoneNumber: String
        get() = phoneNumber

    fun toDto(): UserProfileDto = UserProfileDto(
        id = id,
        fullName = storedFullName,
        phoneNumber = phoneNumber,
        notificationEnabled = notificationEnabled
    )
}