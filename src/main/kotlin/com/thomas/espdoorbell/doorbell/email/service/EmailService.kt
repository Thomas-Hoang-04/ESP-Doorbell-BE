package com.thomas.espdoorbell.doorbell.email.service

import com.resend.Resend
import com.resend.core.exception.ResendException
import com.resend.services.emails.model.CreateEmailOptions
import com.resend.services.emails.model.CreateEmailResponse
import com.thomas.espdoorbell.doorbell.email.config.ResendConfig
import org.springframework.stereotype.Service
import com.thomas.espdoorbell.doorbell.core.exception.DomainException
import com.thomas.espdoorbell.doorbell.email.model.OTPData
import com.thomas.espdoorbell.doorbell.email.model.OTPPurpose
import com.thomas.espdoorbell.doorbell.email.model.OTPRequest
import com.thomas.espdoorbell.doorbell.email.model.OTPResponse
import com.thomas.espdoorbell.doorbell.email.model.OTPStatus
import com.thomas.espdoorbell.doorbell.email.model.OTPValidationRequest
import com.thomas.espdoorbell.doorbell.user.entity.Users
import com.thomas.espdoorbell.doorbell.user.repository.UserRepository
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import java.text.DecimalFormat
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@Service
class EmailService(
    private val apiConfig: ResendConfig,
    private val userRepo: UserRepository,
    private val r2dbcEntityTemplate: R2dbcEntityTemplate,
) {
    private val resend = Resend(apiConfig.apiKey)

    private val otpMap = mutableMapOf<String, OTPData>()

    private fun genOTP(): String
        = DecimalFormat("000000").format(Random.nextInt(1_000_000))

    @OptIn(ExperimentalTime::class)
    suspend fun sendEmail(req: OTPRequest): OTPResponse {
        try {
            when (req.purpose) {
                OTPPurpose.RESET_PASSWORD -> {
                    userRepo.findByEmailIgnoreCase(req.email) ?: return OTPResponse(
                        OTPStatus.INVALID,
                        "Email not registered"
                    )
                }

                else -> {}
            }
            otpMap[req.email]?.let {
                if (Clock.System.now() - it.timestamp < 2.minutes)
                    return OTPResponse(
                        OTPStatus.TOO_MANY_REQUESTS,
                        "Please wait 2 minutes before requesting another OTP"
                    )
            }
            val otp: String = genOTP()
            val emailParams = CreateEmailOptions.Builder()
                .to(req.email)
                .template(apiConfig.getTemplate(req.purpose, otp))
                .build()
            val data: CreateEmailResponse = resend.emails().send(emailParams)
            otpMap[req.email] = OTPData(
                otp = otp,
                timestamp = Clock.System.now()
            )
            return OTPResponse(OTPStatus.SUCCESS, "OTP sent successfully. Message ID: ${data.id}")
        } catch (e: ResendException) {
            return OTPResponse(OTPStatus.FAILED, "Error sending OTP (email service error): ${e.message}")
        } catch (e: Exception) {
            return OTPResponse(OTPStatus.FAILED, "Error sending OTP: ${e.message}")
        }
    }

    private suspend fun verifyEmail(email: String) {
        userRepo.findByEmailIgnoreCase(email) ?:
            throw DomainException.EntityNotFound.User("email", email)

        val query = Query.query(Criteria.where("email").`is`(email))
        val update = Update.update("is_email_verified", true)
        r2dbcEntityTemplate.update(query, update, Users::class.java).awaitSingleOrNull()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun verifyOTP(req: OTPValidationRequest): OTPResponse {
        return otpMap[req.email]?.run {
            if (Clock.System.now() - this.timestamp > 3.minutes) {
                otpMap.remove(req.email)
                OTPResponse(OTPStatus.EXPIRED, "OTP has expired. Please request a new one.")
            } else if (this.otp == req.otp) {
                otpMap.remove(req.email)
                verifyEmail(req.email)
                OTPResponse(OTPStatus.SUCCESS, "OTP verified successfully.")
            } else {
                OTPResponse(OTPStatus.INVALID, "Invalid OTP. Please try again.")
            }
        } ?: OTPResponse(OTPStatus.FAILED, "OTP not found. Please request a new one.")
    }
}

