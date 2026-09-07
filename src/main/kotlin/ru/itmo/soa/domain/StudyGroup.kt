package ru.itmo.soa.domain

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.util.Date

@Entity
@Table(name = "study_groups")
class StudyGroup(
    @field:Column(nullable = false)
    var name: String,
    @field:Embedded
    var coordinates: Coordinates,
    @field:Column(nullable = false)
    var studentsCount: Long,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var formOfEducation: FormOfEducation,
    @field:Enumerated(EnumType.STRING)
    var semesterEnum: Semester? = null,
    @field:Embedded
    var groupAdmin: Person? = null,
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null
        protected set

    @field:Column(nullable = false, updatable = false)
    var creationDate: Date? = null
        protected set

    @PrePersist
    fun initializeCreationDate() {
        if (creationDate == null) creationDate = Date()
    }

    fun updateFrom(other: StudyGroup) {
        name = other.name
        coordinates = other.coordinates
        studentsCount = other.studentsCount
        formOfEducation = other.formOfEducation
        semesterEnum = other.semesterEnum
        groupAdmin = other.groupAdmin
    }
}
