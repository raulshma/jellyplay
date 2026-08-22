package com.raulshma.jellyplay.core.data.log

import java.io.PrintStream

/**
 * Desktop actual of the [Log] facade: `[LEVEL][tag] message` lines on stdout
 * (stderr for `e`), with stack traces for the throwable-taking overloads.
 * Returns 0 — there is no logcat line count to report.
 */
actual object Log {
    actual fun d(tag: String, message: String): Int {
        printLine(System.out, "D", tag, message)
        return 0
    }

    actual fun d(tag: String, message: String, throwable: Throwable?): Int {
        printLine(System.out, "D", tag, message)
        throwable?.printStackTrace(System.out)
        return 0
    }

    actual fun i(tag: String, message: String): Int {
        printLine(System.out, "I", tag, message)
        return 0
    }

    actual fun w(tag: String, message: String): Int {
        printLine(System.out, "W", tag, message)
        return 0
    }

    actual fun w(tag: String, throwable: Throwable): Int {
        printLine(System.out, "W", tag, throwable.toString())
        throwable.printStackTrace(System.out)
        return 0
    }

    actual fun w(tag: String, message: String, throwable: Throwable?): Int {
        printLine(System.out, "W", tag, message)
        throwable?.printStackTrace(System.out)
        return 0
    }

    actual fun e(tag: String, message: String): Int {
        printLine(System.err, "E", tag, message)
        return 0
    }

    actual fun e(tag: String, message: String, throwable: Throwable?): Int {
        printLine(System.err, "E", tag, message)
        throwable?.printStackTrace(System.err)
        return 0
    }

    private fun printLine(stream: PrintStream, level: String, tag: String, message: String) {
        stream.println("[$level][$tag] $message")
    }
}
