package cn.yzu.timetable

/** Monotonic deadlines survive redirects; a click is never authentication success. */
class SsoLoginWaitPolicy(startedAt: Long) {
    enum class Phase { PREPARING, WAITING_FOR_SSO, MANUAL, VERIFYING, COMPLETE, FAILED }
    var phase = Phase.PREPARING
        private set
    private var deadline = startedAt + 60_000L
    val canSubmit get() = phase == Phase.PREPARING

    fun buttonClicked(): Boolean {
        if (!canSubmit) return false
        phase = Phase.WAITING_FOR_SSO
        return true
    }

    fun beginVerification(now: Long): Boolean {
        if (phase !in setOf(Phase.PREPARING, Phase.WAITING_FOR_SSO, Phase.MANUAL)) return false
        if (expire(now)) return false
        phase = Phase.VERIFYING
        deadline = now + 25_000L
        return true
    }

    fun expire(now: Long): Boolean {
        if (phase in setOf(Phase.MANUAL, Phase.COMPLETE, Phase.FAILED) || now < deadline) return false
        phase = Phase.FAILED
        return true
    }

    fun fail() { if (phase != Phase.COMPLETE) phase = Phase.FAILED }
    fun complete() { if (phase == Phase.VERIFYING) phase = Phase.COMPLETE }
    fun useManualLogin() { if (phase != Phase.COMPLETE) phase = Phase.MANUAL }
}
