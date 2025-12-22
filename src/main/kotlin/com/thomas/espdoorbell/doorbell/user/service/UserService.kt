package com.thomas.espdoorbell.doorbell.user.service

import com.thomas.espdoorbell.doorbell.core.exception.EmailAlreadyInUseException
import com.thomas.espdoorbell.doorbell.core.exception.UserNotFoundException
import com.thomas.espdoorbell.doorbell.core.exception.UsernameAlreadyInUseException
import com.thomas.espdoorbell.doorbell.user.dto.UserDto
import com.thomas.espdoorbell.doorbell.user.dto.UserDeviceAccessDto
import com.thomas.espdoorbell.doorbell.user.entity.Users
import com.thomas.espdoorbell.doorbell.user.repository.UserRepository
import com.thomas.espdoorbell.doorbell.user.repository.UserDeviceAccessRepository
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
    private val userRepository: UserRepository,
    private val userDeviceAccessRepository: UserDeviceAccessRepository,
    private val r2dbcEntityTemplate: R2dbcEntityTemplate,
    private val passwordEncoder: PasswordEncoder
) {
    // ========== READ ==========

    suspend fun listUsers(includeAccessAssignments: Boolean = false): List<UserDto> {
        val users = userRepository.findAll().toList()

        if (users.isEmpty()) return emptyList()

        val ids = users.map { it.id!! }

        return coroutineScope {
            val accessAssignments = if (includeAccessAssignments) async {
                userDeviceAccessRepository
                    .findAllByUserIds(ids)
                    .toList()
                    .groupBy { it.user }
            } else null

            val accessMap = accessAssignments?.await() ?: emptyMap()

            users.map { user ->
                val accessList = accessMap[user.id] ?: emptyList()
                user.toDto(accessList)
            }
        }
    }

    @Transactional(readOnly = true)
    suspend fun getUser(userId: UUID, includeAccessAssignments: Boolean = true): UserDto {
        val user = userRepository.findById(userId)
            ?: throw UserNotFoundException(userId)

        val accessAssignments = if (includeAccessAssignments) {
            userDeviceAccessRepository.findAllByUser(userId).toList()
        } else emptyList()

        return user.toDto(accessAssignments)
    }

    @Transactional(readOnly = true)
    suspend fun findByUsername(username: String, includeAccessAssignments: Boolean = true): UserDto {
        val user = userRepository.findByUsername(username)
            ?: throw UserNotFoundException(username)

        val accessAssignments = if (includeAccessAssignments) {
            userDeviceAccessRepository.findAllByUser(user.id!!).toList()
        } else emptyList()

        return user.toDto(accessAssignments)
    }

    suspend fun isUsernameAvailable(username: String): Boolean =
        !userRepository.existsByUsername(username)

    suspend fun isEmailAvailable(email: String): Boolean =
        !userRepository.existsByEmail(email)

    suspend fun isLoginAvailable(login: String): Boolean =
        !userRepository.existsByLogin(login)

    suspend fun listDeviceAccessForUser(userId: UUID): Flow<UserDeviceAccessDto> =
        userDeviceAccessRepository.findAllByUser(userId).map { it.toDto() }

    // ========== CREATE ==========

    @Transactional
    suspend fun registerUser(request: UserRegisterRequest): UserDto {
        request.username?.let { username ->
            if (userRepository.existsByUsername(username)) {
                throw UsernameAlreadyInUseException(username)
            }
        }

        if (userRepository.existsByEmail(request.email.lowercase())) {
            throw EmailAlreadyInUseException(request.email)
        }

        val user = Users(
            _username = request.username,
            _email = request.email.lowercase(),
            _pwd = passwordEncoder.encode(request.password)
        )
        val savedUser = userRepository.save(user)

        return savedUser.toDto(emptyList())
    }

    // ========== UPDATE ==========

    @Transactional
    suspend fun updateNotificationEnabled(userId: UUID, enabled: Boolean): UserDto {
        userRepository.findById(userId)
            ?: throw UserNotFoundException(userId)

        val query = Query.query(Criteria.where("id").`is`(userId))
        val update = Update.update("notification_enabled", enabled)
        r2dbcEntityTemplate.update(query, update, Users::class.java).awaitSingleOrNull()

        return getUser(userId)
    }

    @Transactional
    suspend fun updateEmail(userId: UUID, newEmail: String): UserDto {
        userRepository.findById(userId)
            ?: throw UserNotFoundException(userId)

        if (userRepository.existsByEmail(newEmail.lowercase())) {
            throw EmailAlreadyInUseException(newEmail)
        }

        val query = Query.query(Criteria.where("id").`is`(userId))
        val update = Update.update("email", newEmail.lowercase())
        r2dbcEntityTemplate.update(query, update, Users::class.java).awaitSingleOrNull()

        return getUser(userId)
    }

    @Transactional
    suspend fun updatePassword(userId: UUID, oldPassword: String, newPassword: String) {
        val user = userRepository.findById(userId)
            ?: throw UserNotFoundException(userId)

        if (!passwordEncoder.matches(oldPassword, user.pwd)) {
            throw org.springframework.security.authentication.BadCredentialsException("Current password is incorrect")
        }

        val query = Query.query(Criteria.where("id").`is`(userId))
        val update = Update.update("password", passwordEncoder.encode(newPassword))
        r2dbcEntityTemplate.update(query, update, Users::class.java).awaitSingleOrNull()
    }

    // ========== DELETE ==========

    @Transactional
    suspend fun deleteUser(userId: UUID) {
        userRepository.findById(userId)
            ?: throw UserNotFoundException(userId)

        userRepository.deleteById(userId)
    }
}
