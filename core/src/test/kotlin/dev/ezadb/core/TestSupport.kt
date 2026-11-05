package dev.ezadb.core

class AssertionFailure(message: String) : AssertionError(message)

fun expect(condition: Boolean, message: () -> String = { "expectation failed" }) {
    if (!condition) throw AssertionFailure(message())
}

fun <T> expectEquals(expected: T, actual: T, label: String = "") {
    if (expected != actual) throw AssertionFailure("$label expected <$expected> but was <$actual>")
}

fun expectContains(haystack: String, needle: String) {
    if (!haystack.contains(needle)) throw AssertionFailure("expected <$haystack> to contain <$needle>")
}
