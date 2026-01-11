package com.thomas.espdoorbell.doorbell.email.config

import com.resend.services.emails.model.Template
import com.thomas.espdoorbell.doorbell.email.model.OTPPurpose
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "resend")
data class ResendConfig(
    val apiKey: String,

    @get:ConfigurationProperties(value = "resend.verify-email-template")
    val verifyEmailTemplate: String,

    @get:ConfigurationProperties(value = "resend.reset-password-template")
    val resetPasswordTemplate: String,
) {
    fun getTemplate(purpose: OTPPurpose, otp: String): Template
        = Template.Builder().id(
            when (purpose) {
                OTPPurpose.VERIFY_EMAIL -> verifyEmailTemplate
                OTPPurpose.RESET_PASSWORD -> resetPasswordTemplate
            }
        ).addVariable("OTP", otp).build()
}