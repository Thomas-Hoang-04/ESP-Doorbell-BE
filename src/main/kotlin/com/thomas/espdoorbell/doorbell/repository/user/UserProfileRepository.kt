package com.thomas.espdoorbell.doorbell.repository.user

import com.thomas.espdoorbell.doorbell.model.entity.user.UserProfiles
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.Optional

@Repository
interface UserProfileRepository : JpaRepository<UserProfiles, UUID> {
    @Query("select * from user_profiles where email = :email", nativeQuery = true)
    fun findByEmail(@Param("email") email: String): Optional<UserProfiles>
}
