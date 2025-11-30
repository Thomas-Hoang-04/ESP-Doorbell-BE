package com.thomas.espdoorbell.doorbell.service.user

import com.thomas.espdoorbell.doorbell.model.dto.user.UserCredentialDto
import com.thomas.espdoorbell.doorbell.model.dto.user.UserDeviceAccessDto
import com.thomas.espdoorbell.doorbell.model.entity.user.UserCredentials
import com.thomas.espdoorbell.doorbell.repository.user.UserCredentialRepository
import com.thomas.espdoorbell.doorbell.repository.user.UserDeviceAccessRepository
import com.thomas.espdoorbell.doorbell.repository.user.UserProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.collections.emptyList

@Service
class UserService(
    private val credentialRepository: UserCredentialRepository,
    private val userDeviceAccessRepository: UserDeviceAccessRepository,
    private val userProfileRepository: UserProfileRepository,
) {
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

    private suspend fun extractDto(user: UserCredentials, includeAccessAssignments: Boolean): UserCredentialDto {
        val userProfile = userProfileRepository.findById(user.id)
            ?: throw IllegalArgumentException("User profile for user id ${user.id} was not found")

        val accessAssignments = if (includeAccessAssignments) {
            userDeviceAccessRepository.findAllByUserId(user.id).toList()
        } else emptyList()

        return user.toDto(userProfile, accessAssignments)
    }

    @Transactional(readOnly = true)
    suspend fun getUser(userId: UUID, includeAccessAssignments: Boolean = true): UserCredentialDto {
        val user = credentialRepository.findById(userId)
            ?: throw IllegalArgumentException("User with id $userId was not found")

        val userProfile = userProfileRepository.findById(userId)
            ?: throw IllegalArgumentException("User profile for user id $userId was not found")

        val accessAssignments = if (includeAccessAssignments) {
            userDeviceAccessRepository.findAllByUserId(userId).toList()
        } else emptyList()

        return user.toDto(userProfile, accessAssignments)
    }

    @Transactional(readOnly = true)
    suspend fun findByUsername(username: String, includeAccessAssignments: Boolean = true): UserCredentialDto {
        val users = credentialRepository.findByUsername(username).awaitSingleOrNull()
            ?: throw IllegalArgumentException("User with username $username not found")

        return extractDto(users, includeAccessAssignments)
    }

    @Transactional
    suspend fun registerUser(userCredentials: UserCredentials) {
        // TODO: Implement user registration
    }

    suspend fun listDeviceAccessForUser(userId: UUID): Flow<UserDeviceAccessDto> =
        userDeviceAccessRepository.findAllByUserId(userId).map { it.toDto() }
}
