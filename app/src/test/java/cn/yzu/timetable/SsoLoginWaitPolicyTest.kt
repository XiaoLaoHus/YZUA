package cn.yzu.timetable

import org.junit.Assert.*
import org.junit.Test

class SsoLoginWaitPolicyTest {
    @Test fun clickedButtonWaitsForReturnButCannotSubmitTwice() {
        val flow = SsoLoginWaitPolicy(0)
        assertTrue(flow.canSubmit)
        assertTrue(flow.buttonClicked())
        assertEquals(SsoLoginWaitPolicy.Phase.WAITING_FOR_SSO, flow.phase)
        assertFalse(flow.canSubmit)
        assertFalse(flow.buttonClicked())
        assertTrue(flow.expire(60_000))
        assertEquals(SsoLoginWaitPolicy.Phase.FAILED, flow.phase)
        assertFalse(flow.beginVerification(60_001))
    }

    @Test fun pageLoadsCannotExtendNativeDeadline() {
        val flow = SsoLoginWaitPolicy(1_000)
        assertFalse(flow.expire(60_999))
        flow.buttonClicked()
        assertTrue(flow.expire(61_000))
        assertFalse(flow.canSubmit)
    }

    @Test fun verificationAndCompletionAreTerminalAndIgnoreLateCallbacks() {
        val flow = SsoLoginWaitPolicy(0)
        assertTrue(flow.beginVerification(1_000))
        assertFalse(flow.beginVerification(1_001))
        assertFalse(flow.canSubmit)
        flow.complete()
        assertEquals(SsoLoginWaitPolicy.Phase.COMPLETE, flow.phase)
        assertFalse(flow.expire(100_000))
        flow.fail()
        assertEquals(SsoLoginWaitPolicy.Phase.COMPLETE, flow.phase)
    }

    @Test fun manualModeWaitsForUserButApiVerificationIsBounded() {
        val flow = SsoLoginWaitPolicy(0)
        flow.useManualLogin()
        assertFalse(flow.canSubmit)
        assertFalse(flow.expire(1_000_000))
        assertTrue(flow.beginVerification(1_000_000))
        assertFalse(flow.expire(1_024_999))
        assertTrue(flow.expire(1_025_000))
        flow.useManualLogin()
        assertEquals(SsoLoginWaitPolicy.Phase.MANUAL, flow.phase)
        assertFalse(flow.canSubmit)
    }
}
