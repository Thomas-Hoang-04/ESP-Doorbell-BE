package com.thomas.espdoorbell.doorbell.user.service

import com.thomas.espdoorbell.doorbell.user.dto.UserCredentialDto
import com.thomas.espdoorbell.doorbell.user.dto.UserDeviceAccessDto
import com.thomas.espdoorbell.doorbell.user.entity.UserCredentials
import com.thomas.espdoorbell.doorbell.user.entity.UserProfiles
import com.thomas.espdoorbell.doorbell.user.repository.UserCredentialRepository
import com.thomas.espdoorbell.doorbell.user.repository.UserDeviceAccessRepository
import com.thomas.espdoorbell.doorbell.user.repository.UserProfileRepository
import com.thomas.espdoorbell.doorbell.user.request.ProfileUpdateRequest
import com.thomas.espdoorbell.doorbell.user.request.UserRegisterRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class UserService(
    private val credentialRepository: UserCredentialRepository,
    private val userDeviceAccessRepository: UserDeviceAccessRepository,
    private val userProfileRepository: UserProfileRepository,
    private val r2dbcEntityTemplate: R2dbcEntityTemplate,
    private val passwordEncoder: PasswordEncoder
) {
    // ========== READ ==========

    suspend fun listUsers(includeAccessAssignments: Boolean = false): List<UserCredentialDto> {
        val users = credentialRepository.findAll().toList()

        if (users.isEmpty()) return emptyList()

        val ids = users.map { it.id }

        return coroutineScope {
            val profiles = async {
                userProfileRepository
                    .findAllById(ids)
                    .toList()
                    .associateBy { it.id }
            }

            val accessAssignments = if (includeAccessAssignments) async {
                userDeviceAccessRepository
                    .findAllByUserIds(ids)
                    .toList()
                    .groupBy { it.user }
            } else null

            val profileMap = profiles.await()
            val accessMap = accessAssignments?.await() ?: emptyMap()

            users.mapNotNull { user ->
                val id = user.id
                val profile = profileMap[id] ?: return@mapNotNull null
                val accessList = accessMap[id] ?: emptyList()

                user.toDto(profile, accessList)
            }
        }
    }

    @Transactional(readOnly = true)
    suspend fun getUser(userId: UUID, includeAccessAssignments: Boolean = true): UserCredentialDto {
        val user = credentialRepository.findById(userId)
            ?: throw NoSuchElementException("User with id $userId was not found")

        val userProfile = userProfileRepository.findById(userId)
            ?: throw NoSuchElementException("User profile for user id $userId was not found")

        val accessAssignments = if (includeAccessAssignments) {
            userDeviceAccessRepository.findAllByUserId(userId).toList()
        } else emptyList()

        return user.toDto(userProfile, accessAssignments)
    }

    @Transactional(readOnly = true)
    suspend fun findByUsername(username: String, includeAccessAssignments: Boolean = true): UserCredentialDto {
        val user = credentialRepository.findByUsername(username)
            ?: throw NoSuchElementException("User with username $username not found")

        return extractDto(user, includeAccessAssignments)
    }

    suspend fun isUsernameAvailable(username: String): Boolean =
        !credentialRepository.existsByUsername(username)

    suspend fun isEmailAvailable(email: String): Boolean =
        !credentialRepository.existsByEmail(email.lowercase())

    suspend fun listDeviceAccessForUser(userId: UUID): Flow<UserDeviceAccessDto> =
        userDeviceAccessRepository.findAllByUserId(userId).map { it.toDto() }

    // ========== CREATE ==========

    @Transactional
    suspend fun registerUser(request: UserRegisterRequest): UserCredentialDto {
        // Check username availability
        request.username?.let { username ->
            if (credentialRepository.existsByUsername(username)) {
                throw IllegalStateException("Username $username is already taken")
            }
        }

        // Check email availability
        if (credentialRepository.existsByEmail(request.email.lowercase())) {
            throw IllegalStateException("Email ${request.email} is already registered")
        }

        // Create credentials
        val credentials = UserCredentials(
            username = request.username,
            rawEmail = request.email.lowercase(),
            passwordHash = passwordEncoder.encode(request.password)
        )
        val savedCredentials = credentialRepository.save(credentials)

        // Create profile
        val profile = UserProfiles(
            user = savedCredentials.id,
            storedFullName = request.displayName,
            phoneNumber = request.phoneNumber ?: ""
        )
        val savedProfile = userProfileRepository.save(profile)

        return savedCredentials.toDto(savedProfile, emptyList())
    }

    // ========== UPDATE ==========

    @Transactional
    suspend fun updateUserProfile(userId: UUID, request: ProfileUpdateRequest): UserCredentialDto {
        // Verify user exists
        credentialRepository.findById(userId)
            ?: throw NoSuchElementException("User with id $userId was not found")

        // Update credentials if email changed
        request.email?.let { newEmail ->
            if (credentialRepository.existsByEmail(newEmail.lowercase())) {
                throw IllegalStateException("Email $newEmail is already in use")
            }
            val query = Query.query(Criteria.where("id").`is`(userId))
            val update = Update.update("email", newEmail.lowercase())
            r2dbcEntityTemplate.update(query, update, UserCredentials::class.java)
        }

        // Update password if provided
        request.newPassword?.let { updatePassword(userId, it) }

        // Update profile
        val profileUpdates = mutableMapOf<String, Any?>()
        request.displayName?.let { profileUpdates["full_name"] = it }
        request.phoneNumber?.let { profileUpdates["phone_number"] = it }

        if (profileUpdates.isNotEmpty()) {
            val query = Query.query(Criteria.where("id").`is`(userId))
            var update: Update? = null
            for ((key, value) in profileUpdates) {
                update = update?.set(key, value) ?: Update.update(key, value)
            }
            update?.let { r2dbcEntityTemplate.update(query, it, UserProfiles::class.java) }
        }

        return getUser(userId)
    }

    @Transactional
    suspend fun updatePassword(userId: UUID, newPassword: String) {
        credentialRepository.findById(userId)
            ?: throw NoSuchElementException("User with id $userId was not found")

        val query = Query.query(Criteria.where("id").`is`(userId))
        val update = Update.update("password", passwordEncoder.encode(newPassword))
        r2dbcEntityTemplate.update(query, update, UserCredentials::class.java).awaitSingleOrNull()
    }

    // ========== DELETE ==========

    @Transactional
    suspend fun deleteUser(userId: UUID) {
        credentialRepository.findById(userId)
            ?: throw NoSuchElementException("User with id $userId was not found")

        // Delete profile first (FK constraint)
        userProfileRepository.deleteById(userId)
        // Delete credentials
        credentialRepository.deleteById(userId)
    }

    // ========== PRIVATE ==========

    private suspend fun extractDto(user: UserCredentials, includeAccessAssignments: Boolean): UserCredentialDto {
        val userProfile = userProfileRepository.findById(user.id)
            ?: throw NoSuchElementException("User profile for user id ${user.id} was not found")

        val accessAssignments = if (includeAccessAssignments) {
            userDeviceAccessRepository.findAllByUserId(user.id).toList()
        } else emptyList()

        return user.toDto(userProfile, accessAssignments)
    }
}

