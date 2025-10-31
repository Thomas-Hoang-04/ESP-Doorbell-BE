package com.thomas.espdoorbell.doorbell.service.user

import com.thomas.espdoorbell.doorbell.model.dto.user.UserCredentialDto
import com.thomas.espdoorbell.doorbell.model.dto.user.UserDeviceAccessDto
import com.thomas.espdoorbell.doorbell.model.entity.user.UserCredentials
import com.thomas.espdoorbell.doorbell.repository.user.UserCredentialRepository
import com.thomas.espdoorbell.doorbell.repository.user.UserDeviceAccessRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val credentialRepository: UserCredentialRepository,
    private val userDeviceAccessRepository: UserDeviceAccessRepository,
) {

    @Transactional(readOnly = true)
    fun listUsers(includeAccessAssignments: Boolean = false): List<UserCredentialDto> =
        credentialRepository.findAll().map { it.toDto(includeAccessAssignments) }

    @Transactional(readOnly = true)
    fun getUser(userId: UUID, includeAccessAssignments: Boolean = true): UserCredentialDto {
        val user = credentialRepository.findByIdOrNull(userId)
            ?: throw EntityNotFoundException("User with id $userId was not found")

        return if (includeAccessAssignments) {
            val access = userDeviceAccessRepository.findAllByUserId(userId).map { it.toDto() }
            user.toDto(includeAccessAssignments = false).copy(deviceAccess = access)
        } else {
            user.toDto(includeAccessAssignments = false)
        }
    }

    @Transactional(readOnly = true)
    fun findByUsername(username: String, includeAccessAssignments: Boolean = false): UserCredentialDto? =
        credentialRepository.findByUsername(username)
            .map { it.toDto(includeAccessAssignments) }
            .orElse(null)

    @Transactional(readOnly = true)
    fun findByOauthProviderId(oauthProviderId: String, includeAccessAssignments: Boolean = false): UserCredentialDto? =
        credentialRepository.findByOauthProviderId(oauthProviderId)
            .map { it.toDto(includeAccessAssignments) }
            .orElse(null)

    @Transactional
    // TODO: Update HTTP request format here
    fun registerUser(userCredentials: UserCredentials): UserCredentialDto =
        credentialRepository.save(userCredentials).toDto(includeAccessAssignments = false)

    @Transactional(readOnly = true)
    fun listDeviceAccessForUser(userId: UUID): List<UserDeviceAccessDto> =
        userDeviceAccessRepository.findAllByUserId(userId).map { it.toDto() }
}
