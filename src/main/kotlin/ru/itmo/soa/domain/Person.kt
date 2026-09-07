package ru.itmo.soa.domain

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.ZonedDateTime

@Embeddable
class Person(
    @field:Column(name = "admin_name")
    var name: String = "",
    @field:Column(name = "admin_birthday")
    var birthday: ZonedDateTime? = null,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "admin_hair_color")
    var hairColor: Color? = null,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "admin_nationality")
    var nationality: Country? = null,
    @field:Embedded
    var location: Location? = null,
)
