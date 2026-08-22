package com.raulshma.jellyplay.core.network

import org.slf4j.LoggerFactory

actual object NetworkLog {
    private fun logger(tag: String) = LoggerFactory.getLogger(tag)

    actual fun d(tag: String, message: String) {
        logger(tag).debug(message)
    }

    actual fun w(tag: String, message: String) {
        logger(tag).warn(message)
    }

    actual fun w(tag: String, message: String, error: Throwable?) {
        logger(tag).warn(message, error)
    }

    actual fun e(tag: String, message: String, error: Throwable?) {
        logger(tag).error(message, error)
    }

    actual fun isDebugEnabled(tag: String): Boolean = logger(tag).isDebugEnabled
}
