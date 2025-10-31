package com.thomas.espdoorbell.doorbell.repository.event

import com.thomas.espdoorbell.doorbell.model.entity.events.EventMedia
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EventMediaRepository : JpaRepository<EventMedia, UUID>
