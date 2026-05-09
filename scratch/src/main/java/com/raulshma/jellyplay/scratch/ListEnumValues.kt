package com.raulshma.jellyplay.scratch

import org.jellyfin.sdk.model.api.GeneralCommandType

fun main() {
    println("Values of GeneralCommandType:")
    GeneralCommandType.values().forEach {
        println("- $it")
    }
}
