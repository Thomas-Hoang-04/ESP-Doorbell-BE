package com.thomas.espdoorbell.doorbell.model.entity

import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "events")
class Events(

): BaseEntity()