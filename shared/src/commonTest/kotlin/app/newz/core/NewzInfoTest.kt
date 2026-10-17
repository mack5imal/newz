package app.newz.core

import kotlin.test.Test
import kotlin.test.assertTrue

class NewzInfoTest {
    @Test
    fun userAgentIsNotEmpty() {
        assertTrue(NewzInfo.USER_AGENT.isNotBlank())
    }
}
