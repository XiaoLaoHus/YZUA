package cn.yzu.timetable

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SsoLoginScriptTest {
    @Test fun credentialsAreEncodedAndOfficialSubmitHandlerCannotBeBypassed() {
        val script = SsoLoginScript.build("20261234", "p@ss'\\word")

        assertTrue(script.contains("form.querySelector"))
        assertTrue(script.contains("atob"))
        assertFalse(script.contains("form.submit"))
        assertFalse(script.contains("20261234"))
        assertFalse(script.contains("p@ss"))
    }
}
