package com.thomas.espdoorbell.doorbell.model.entity.user

import com.thomas.espdoorbell.doorbell.model.dto.user.UserProfileDto
import com.thomas.espdoorbell.doorbell.model.entity.base.BaseEntity
import org.springframework.data.annotation.Transient
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetTime
import java.util.UUID

@Table("user_profiles")
class UserProfiles(
    @Transient
    private val user: UUID,

    @Column("display_name")
    private val _displayName: String,

    @Column("phone_number")
    private val phoneNum: String? = null,

    @Column("email")
    private val email: String? = null,

    @Column("notification_enabled")
    private val notificationsEnabled: Boolean = true,

    @Column("quiet_hours_start")
    private val quietHoursStart: OffsetTime? = null,

    @Column("quiet_hours_end")
    private val quietHoursEnd: OffsetTime? = null,
): BaseEntity(id = user) {

    init { validate() }

    override fun validate() {
        require(!phoneNum.isNullOrBlank() || !email.isNullOrBlank()) {
            "At least one contact method (phone number or email) must be provided"
        }

        phoneNum?.let {
            val localPattern = Regex("^0[1-9][0-9]{8}$")
            val e164Pattern = Regex("^\\+[1-9]\\d{1,14}$")
            require(it.matches(localPattern) || it.matches(e164Pattern)) {
                "Phone number must be in valid E.164 format or local format"
            }
        }

        email?.let {
            require(it.matches(Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$"))) {
                "Email must be in a valid format"
            }
        }
    }

    val displayName: String
        get() = _displayName

    fun toDto(): UserProfileDto = UserProfileDto(
        id = id,
        displayName = _displayName,
        emailAddress = email,
        phoneNumber = phoneNum,
        notificationsEnabled = notificationsEnabled,
        quietHoursStart = quietHoursStart,
        quietHoursEnd = quietHoursEnd,
    )
}