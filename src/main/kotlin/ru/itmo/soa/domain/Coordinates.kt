package ru.itmo.soa.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class Coordinates(
    @field:Column(name = "coordinate_x", nullable = false)
    var x: Float = 0f,
    @field:Column(name = "coordinate_y", nullable = false)
    var y: Float = 0f,
)
