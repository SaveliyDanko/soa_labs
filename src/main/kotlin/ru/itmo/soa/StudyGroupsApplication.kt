package ru.itmo.soa

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class StudyGroupsApplication

fun main(args: Array<String>) {
    runApplication<StudyGroupsApplication>(*args)
}
