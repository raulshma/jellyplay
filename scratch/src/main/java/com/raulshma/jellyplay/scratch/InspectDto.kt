package com.raulshma.jellyplay.scratch

import org.jellyfin.sdk.model.api.ClientCapabilitiesDto
import kotlin.reflect.full.memberProperties

fun main() {
    println("Fields of ClientCapabilitiesDto:")
    ClientCapabilitiesDto::class.memberProperties.forEach {
        println("- ${it.name}: ${it.returnType}")
    }
}
