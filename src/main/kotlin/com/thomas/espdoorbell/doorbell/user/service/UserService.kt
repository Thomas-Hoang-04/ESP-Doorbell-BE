package com.thomas.espdoorbell.doorbell.user.service

import com.thomas.espdoorbell.doorbell.core.exception.DomainException
import com.thomas.espdoorbell.doorbell.user.dto.UserDto
import com.thomas.espdoorbell.doorbell.user.dto.UserDeviceAccessDto
import com.thomas.espdoorbell.doorbell.user.entity.Users
import com.thomas.espdoorbell.doorbell.user.repository.UserRepository
import com.thomas.espdoorbell.doorbell.user.repository.UserDeviceAccessRepository
import com.thomas.espdoorbell.doorbell.user.request.UserRegisterRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

@Service
class UserService(
    private val userRepository: UserRepository,
    private val userDeviceAccessRepository: UserDeviceAccessRepository,
    private val r2dbcEntityTemplate: R2dbcEntityTemplate,
    private val passwordEncoder: PasswordEncoder
) {

    @Transactional(readOnly = true)
    suspend fun getUser(userId: UUID, includeAccessAssignments: Boolean = true): UserDto {
        val user = userRepository.findById(userId)
            ?: throw DomainException.EntityNotFound.User("id", userId.toString())

        val accessAssignments = if (includeAccessAssignments) {
            userDeviceAccessRepository.findAllByUser(userId).toList()
        } else emptyList()

        return user.toDto(accessAssignments)
    }

    @Transactional(readOnly = true)
    @Suppress("unused")
    suspend fun findByUsername(username: String, includeAccessAssignments: Boolean = true): UserDto {
        val user = userRepository.findByUsername(username)
            ?: throw DomainException.EntityNotFound.User("username", username)

        val accessAssignments = if (includeAccessAssignments) {
            userDeviceAccessRepository.findAllByUser(user.id!!).toList()
        } else emptyList()

        return user.toDto(accessAssignments)
    }

    suspend fun isUsernameAvailable(username: String): Boolean =
        !userRepository.existsByUsername(username)

    suspend fun isEmailAvailable(email: String): Boolean =
        !userRepository.existsByEmail(email)

    suspend fun isLoginExists(login: String): Boolean =
        userRepository.existsByLogin(login)

    suspend fun listDeviceAccessForUser(userId: UUID): Flow<UserDeviceAccessDto> =
        userDeviceAccessRepository.findAllByUser(userId).map { it.toDto() }



    @Transactional
    suspend fun registerUser(request: UserRegisterRequest): UserDto {
        request.username?.let { username ->
            if (userRepository.existsByUsername(username)) {
                throw DomainException.EntityConflict.UsernameAlreadyInUse(username)
            }
        }

        if (userRepository.existsByEmail(request.email.lowercase())) {
            throw DomainException.EntityConflict.EmailAlreadyInUse(request.email)
        }

        val user = Users(
            username = request.username,
            email = request.email.lowercase(),
            pwd = passwordEncoder.encode(request.password)
        )
        val savedUser = userRepository.save(user)

        return savedUser.toDto(emptyList())
    }



    @Transactional
    suspend fun updateLoginTimestamp(userId: UUID) {
        userRepository.findById(userId)
            ?: throw DomainException.EntityNotFound.User("id", userId.toString())

        val query = Query.query(Criteria.where("id").`is`(userId))
        val update = Update.update("last_login", OffsetDateTime.now())
        r2dbcEntityTemplate.update(query, update, Users::class.java).awaitSingleOrNull()
    }

    @Transactional
    suspend fun updateUsername(userId: UUID, newUsername: String?): Boolean {
        userRepository.findById(userId)
            ?: throw DomainException.EntityNotFound.User("id", userId.toString())

        if (newUsername != null && userRepository.existsByUsername(newUsername)) {
            throw DomainException.EntityConflict.UsernameAlreadyInUse(newUsername)
        }

        val query = Query.query(Criteria.where("id").`is`(userId))
        val update = Update.update("username", newUsername)
        val result = r2dbcEntityTemplate.update(query, update, Users::class.java).awaitSingleOrNull()
        return (result ?: 0L) > 0
    }

    @Transactional
    suspend fun updateEmail(userId: UUID, newEmail: String): Boolean {
        userRepository.findById(userId)
            ?: throw DomainException.EntityNotFound.User("id", userId.toString())

        if (userRepository.existsByEmail(newEmail.lowercase())) {
            throw DomainException.EntityConflict.EmailAlreadyInUse(newEmail)
        }

        val query = Query.query(Criteria.where("id").`is`(userId))
        val update = Update.update("email", newEmail.lowercase())
        val result = r2dbcEntityTemplate.update(query, update, Users::class.java).awaitSingleOrNull()
        return (result ?: 0L) > 0
    }

    @Transactional
    suspend fun updatePassword(userId: UUID, oldPassword: String, newPassword: String): Boolean {
        val user = userRepository.findById(userId)
            ?: throw DomainException.EntityNotFound.User("id", userId.toString())

        if (!passwordEncoder.matches(oldPassword, user.pwd)) {
            throw BadCredentialsException("Current password is incorrect")
        }

        val query = Query.query(Criteria.where("id").`is`(userId))
        val update = Update.update("password", passwordEncoder.encode(newPassword))
        return r2dbcEntityTemplate.update(query, update, Users::class.java).awaitSingleOrNull() != null
    }

    @Transactional
    suspend fun resetPassword(login: String, newPassword: String): Boolean {
        val user = userRepository.findByLogin(login)
            ?: throw DomainException.EntityNotFound.User("login", login)

        val query = Query.query(Criteria.where("id").`is`(user.id!!))
        val update = Update.update("password", passwordEncoder.encode(newPassword))
        return r2dbcEntityTemplate.update(query, update, Users::class.java).awaitSingleOrNull() != null
    }



    @Transactional
    suspend fun deleteUser(userId: UUID) {
        userRepository.findById(userId)
            ?: throw DomainException.EntityNotFound.User("id", userId.toString())

        userRepository.deleteById(userId)
    }
}
