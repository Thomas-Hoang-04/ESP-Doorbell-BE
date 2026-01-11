package com.thomas.espdoorbell.doorbell.email.controller

import com.thomas.espdoorbell.doorbell.email.model.OTPRequest
import com.thomas.espdoorbell.doorbell.email.model.OTPResponse
import com.thomas.espdoorbell.doorbell.email.model.OTPStatus
import com.thomas.espdoorbell.doorbell.email.model.OTPValidationRequest
import com.thomas.espdoorbell.doorbell.email.service.EmailService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping

@RestController
@RequestMapping("/api/verify")
class EmailController(
    private val emailService: EmailService,
) {
    @PostMapping("/send")
    suspend fun sendOTP(@RequestBody req: OTPRequest): ResponseEntity<OTPResponse>
        = emailService.sendEmail(req).run {
            when (this.status) {
                OTPStatus.SUCCESS -> ResponseEntity.ok().body(this)
                OTPStatus.TOO_MANY_REQUESTS -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(this)
                OTPStatus.INVALID -> ResponseEntity.badRequest().body(this)
                else -> ResponseEntity.internalServerError().body(this)
            }
        }

    @PostMapping("/validate")
    suspend fun validateOTP(@RequestBody req: OTPValidationRequest): ResponseEntity<OTPResponse>
        = emailService.verifyOTP(req).run {
            when (this.status) {
                OTPStatus.SUCCESS -> ResponseEntity.ok().body(this)
                OTPStatus.EXPIRED -> ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(this)
                OTPStatus.INVALID -> ResponseEntity.badRequest().body(this)
                else -> ResponseEntity.internalServerError().body(this)
            }
        }
}
