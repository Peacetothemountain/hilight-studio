package com.hilight.studio

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRulePersistenceTest {

    @Test
    fun `face-down notification rule survives preference round trip`() {
        val original = AppRule(
            pkg = "com.example",
            label = "Example",
            trigger = Trigger.NOTIFICATION,
            onlyWhenFaceDown = true,
        )

        assertTrue(AppRule.fromJson(original.toPrefsJson()).onlyWhenFaceDown)
    }

    @Test
    fun `older stored rules default face-down gate to off`() {
        val legacy = JSONObject()
            .put("pkg", "com.example")
            .put("label", "Example")

        assertFalse(AppRule.fromJson(legacy).onlyWhenFaceDown)
    }
}
