package com.wayfare.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaystackReturnMatcherTest {

    @Test
    fun `matches vhaya custom scheme payment return`() {
        assertEquals(
            "ref-123",
            PaystackReturnMatcher.extractCompletedReference(
                url = "vhaya://payment-return?trxref=ref-123&reference=ref-123",
                apiBaseUrl = "https://api.vhaya.co.za",
                fallbackReference = "fallback-ref"
            )
        )
        assertEquals(
            "fallback-ref",
            PaystackReturnMatcher.extractCompletedReference(
                url = "vhaya://payment-return",
                apiBaseUrl = "https://api.vhaya.co.za",
                fallbackReference = "fallback-ref"
            )
        )
    }

    @Test
    fun `matches backend callback url and generic callback params`() {
        assertEquals(
            "pay-456",
            PaystackReturnMatcher.extractCompletedReference(
                url = "https://api.vhaya.co.za/api/payments/callback?reference=pay-456",
                apiBaseUrl = "https://api.vhaya.co.za",
                fallbackReference = "fallback-ref"
            )
        )
        assertEquals(
            "pay-789",
            PaystackReturnMatcher.extractCompletedReference(
                url = "https://vhaya.co.za/done?trxref=pay-789&reference=pay-789",
                apiBaseUrl = "https://api.vhaya.co.za",
                fallbackReference = "fallback-ref"
            )
        )
    }

    @Test
    fun `does not intercept paystack checkout or 3ds pages`() {
        assertNull(
            PaystackReturnMatcher.extractCompletedReference(
                url = "https://checkout.paystack.com/xyz987?reference=pay-000",
                apiBaseUrl = "https://api.vhaya.co.za",
                fallbackReference = "fallback-ref"
            )
        )
        assertNull(
            PaystackReturnMatcher.extractCompletedReference(
                url = "https://standard.paystack.co/close?trxref=pay-000&reference=pay-000",
                apiBaseUrl = "https://api.vhaya.co.za",
                fallbackReference = "fallback-ref"
            )
        )
        assertNull(
            PaystackReturnMatcher.extractCompletedReference(
                url = "https://acs.examplebank.co.za/3ds/challenge?creq=abc",
                apiBaseUrl = "https://api.vhaya.co.za",
                fallbackReference = "fallback-ref"
            )
        )
    }

    @Test
    fun `validates safe checkout urls`() {
        org.junit.Assert.assertTrue(PaystackReturnMatcher.isSafeCheckoutUrl("https://checkout.paystack.com/abc123"))
        org.junit.Assert.assertFalse(PaystackReturnMatcher.isSafeCheckoutUrl("javascript:alert(1)"))
        org.junit.Assert.assertFalse(PaystackReturnMatcher.isSafeCheckoutUrl("file:///etc/passwd"))
        org.junit.Assert.assertFalse(PaystackReturnMatcher.isSafeCheckoutUrl(""))
    }
}
