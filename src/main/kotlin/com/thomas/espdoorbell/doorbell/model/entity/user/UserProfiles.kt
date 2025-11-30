package com.thomas.espdoorbell.doorbell.model.entity.user

import com.thomas.espdoorbell.doorbell.model.dto.user.UserProfileDto
import com.thomas.espdoorbell.doorbell.model.entity.base.BaseEntity
import org.springframework.data.annotation.Transient
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDate
import java.time.OffsetTime
import java.time.ZoneId
import java.util.UUID

@Table("user_profiles")
class UserProfiles(
    @Transient
    private val user: UUID,

    @Column("full_name")
    private val storedFullName: String,

    @Column("phone_number")
    private val phoneNumber: String,

    @Column("dob")
    private val dateOfBirth: LocalDate? = null,

    @Column("timezone")
    private val timezone: String = "Asia/Ho_Chi_Minh",

    @Column("notification_enabled")
    private val notificationEnabled: Boolean = true,

    @Column("quiet_hours_start")
    private val quietHoursStart: OffsetTime? = null,

    @Column("quiet_hours_end")
    private val quietHoursEnd: OffsetTime? = null,
): BaseEntity(id = user) {

    init { validate() }

    override fun validate() {
        val localPattern = Regex("^0[1-9][0-9]+$")
        val e164Pattern = Regex("^\\+[1-9]\\d{1,14}$")
        require(phoneNumber.matches(localPattern) || phoneNumber.matches(e164Pattern)) {
            "Phone number must be in valid E.164 format or normalized local format"
        }
        runCatching { ZoneId.of(timezone) }
            .getOrElse { throw IllegalArgumentException("Timezone must be a valid IANA identifier") }
    }

    val fullName: String
        get() = storedFullName

    val normalizedPhoneNumber: String
        get() = phoneNumber

    fun toDto(): UserProfileDto = UserProfileDto(
        id = id,
        fullName = storedFullName,
        phoneNumber = phoneNumber,
        dateOfBirth = dateOfBirth,
        timezone = timezone,
        notificationEnabled = notificationEnabled,
        quietHoursStart = quietHoursStart,
        quietHoursEnd = quietHoursEnd,
    )
}