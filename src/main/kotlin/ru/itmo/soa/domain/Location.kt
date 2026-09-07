package ru.itmo.soa.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class Location(
    @field:Column(name = "admin_location_x")
    var x: Long = 0,
    @field:Column(name = "admin_location_y")
    var y: Double? = null,
    @field:Column(name = "admin_location_z")
    var z: Double? = null,
)
