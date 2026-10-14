package com.strobingn.wildlifefieldops.pricing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyInputTest {
    @Test fun parsesTypedMoney() {
        assertEquals(1250.5, MoneyInput.parse("$1,250.50")!!, 0.0)
        assertEquals(8.875, MoneyInput.parse("8.875%")!!, 0.0)
        assertEquals(300.0, MoneyInput.parse(" 300 ")!!, 0.0)
        assertEquals(1000.0, MoneyInput.parse("1,000")!!, 0.0)
        assertEquals(29.8, MoneyInput.parse("29.8")!!, 0.0)
        assertNull(MoneyInput.parse(""))
        assertNull(MoneyInput.parse("abc"))
    }
}
