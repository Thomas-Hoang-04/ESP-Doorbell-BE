package com.thomas.espdoorbell.doorbell.model.entity.user

import com.thomas.espdoorbell.doorbell.model.entity.base.BaseEntityNoAutoId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.OffsetTime

@Entity
@Table(name = "user_profiles")
class UserProfiles(
    @Column(name = "display_name", nullable = false)
    private val displayName: String,

    @Column(name = "phone_number", length = 16)
    private val phoneNum: String? = null,

    @Column(name = "email")
    private val email: String? = null,

    @Column(name = "notification_enabled")
    private val notificationsEnabled: Boolean = true,

    @Column(name = "quiet_hours_start")
    private val quietHoursStart: OffsetTime? = null,

    @Column(name = "quiet_hours_end")
    private val quietHoursEnd: OffsetTime? = null,

    @OneToOne
    @MapsId
    @JoinColumn(name = "id", referencedColumnName = "id", nullable = false)
    private val cred: UserCredentials,
): BaseEntityNoAutoId() {
    @PrePersist
    @PreUpdate
    fun validateContacts() {
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
}