package com.thomas.espdoorbell.doorbell.repository.user

import com.thomas.espdoorbell.doorbell.model.entity.user.UserProfiles
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserProfileRepository : CoroutineCrudRepository<UserProfiles, UUID> {
    @Query("SELECT * FROM user_profiles WHERE full_name = :name")
    suspend fun findByName(@Param("name") name: String): Flow<UserProfiles>

    @Query("SELECT * FROM user_profiles WHERE phone_number = :phoneNumber")
    suspend fun findByPhoneNumber(@Param("phoneNumber") phoneNumber: String): Flow<UserProfiles>
}