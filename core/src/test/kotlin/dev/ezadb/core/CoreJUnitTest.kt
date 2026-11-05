package dev.ezadb.core

import org.junit.Test

class CoreJUnitTest {
    @Test
    fun allCoreTests() {
        val failures = runAllTests()
        if (failures > 0) throw AssertionError("$failures core test(s) failed")
    }
}
