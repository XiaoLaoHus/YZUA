package cn.yzu.timetable

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SsoLoginScriptTest {
    @Test fun scriptUsesBase64CredentialsAndNativeSubmitWithoutEmbeddingRawValues() {
        val script = SsoLoginScript.build("20261234", "p@ss'\\word")

        assertTrue(script.contains("document.querySelector"))
        assertTrue(script.contains("atob"))
        assertTrue(script.contains("form.submit"))
        assertFalse(script.contains("20261234"))
        assertFalse(script.contains("p@ss"))
    }
}
