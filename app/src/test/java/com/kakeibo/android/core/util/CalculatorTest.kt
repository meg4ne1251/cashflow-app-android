package com.kakeibo.android.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors web/src/__tests__/utils/calc.test.ts to keep the calculator behaviour identical. */
class CalculatorTest {

    @Test fun `evaluates addition`() = assertEquals(300L, Calculator.evaluateExpression("100+200"))
    @Test fun `evaluates subtraction`() = assertEquals(350L, Calculator.evaluateExpression("500-150"))
    @Test fun `evaluates multiplication`() = assertEquals(36L, Calculator.evaluateExpression("12*3"))

    @Test fun `division rounds to nearest integer`() {
        assertEquals(3L, Calculator.evaluateExpression("10/3")) // 3.333 -> 3
        assertEquals(3L, Calculator.evaluateExpression("10/4")) // 2.5 -> 3 (round half up)
    }

    @Test fun `multiply before add`() = assertEquals(14L, Calculator.evaluateExpression("2+3*4"))
    @Test fun `divide before subtract`() = assertEquals(15L, Calculator.evaluateExpression("20-10/2"))
    @Test fun `single number`() = assertEquals(42L, Calculator.evaluateExpression("42"))
    @Test fun `ignores whitespace`() = assertEquals(300L, Calculator.evaluateExpression("  100 + 200 "))
    @Test fun `chains left to right`() = assertEquals(120L, Calculator.evaluateExpression("100+50-30"))

    @Test fun `null for empty`() {
        assertNull(Calculator.evaluateExpression(""))
        assertNull(Calculator.evaluateExpression("   "))
    }

    @Test fun `null for division by zero`() = assertNull(Calculator.evaluateExpression("10/0"))

    @Test fun `null for invalid characters`() {
        assertNull(Calculator.evaluateExpression("10+abc"))
        assertNull(Calculator.evaluateExpression("1e5"))
    }

    @Test fun `null on overflow instead of a wrong wrapped value`() {
        assertNull(Calculator.evaluateExpression("9999999999*9999999999"))
        assertNull(Calculator.evaluateExpression("9000000000000000000+9000000000000000000"))
    }

    @Test fun `null for trailing operator`() = assertNull(Calculator.evaluateExpression("100+"))
    @Test fun `null for leading operator`() = assertNull(Calculator.evaluateExpression("*5"))
    @Test fun `null for consecutive operators`() = assertNull(Calculator.evaluateExpression("5++5"))

    @Test fun `hasOperator detects each operator`() {
        assertTrue(Calculator.hasOperator("1+1"))
        assertTrue(Calculator.hasOperator("1-1"))
        assertTrue(Calculator.hasOperator("1*1"))
        assertTrue(Calculator.hasOperator("1/1"))
    }

    @Test fun `hasOperator false for plain number`() = assertFalse(Calculator.hasOperator("1500"))
    @Test fun `hasOperator false for empty`() = assertFalse(Calculator.hasOperator(""))
}
