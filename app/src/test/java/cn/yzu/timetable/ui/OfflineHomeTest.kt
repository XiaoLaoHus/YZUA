package cn.yzu.timetable.ui

import cn.yzu.timetable.data.urp.DemoUrpDataSource
import cn.yzu.timetable.data.*
import cn.yzu.timetable.data.model.*
import cn.yzu.timetable.data.urp.UrpScheduleDataSource
import cn.yzu.timetable.data.urp.UrpLoginException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineHomeTest {
    private val dispatcher = StandardTestDispatcher()
    private val date = LocalDate.parse("2026-10-28")
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun firstLaunchShowsHomeWithoutAuthentication() {
        val vm = AppViewModel(DemoUrpDataSource())
        assertEquals(AppScreen.Home, vm.state.value.screen)
        assertNull(vm.state.value.session)
        assertFalse(vm.state.value.loading)
    }

    @Test fun importedDataSurvivesNewViewModelWithoutNetworkOrSession() = runTest(dispatcher) {
        val cache = AcademicCache()
        val vm = AppViewModel(DemoUrpDataSource(), cache = cache, dateProvider = { date })
        vm.loginWithSsoCookie("fixture=value"); advanceUntilIdle()
        assertTrue(vm.saveSemester("2026-08-31", "2027-01-17"))
        vm.dismissOnboarding(); vm.logout()
        val offline = object : UrpScheduleDataSource by DemoUrpDataSource() {
            override suspend fun loginWithSsoCookie(account: String, cookie: String): UserSession = error("No startup network")
        }
        val restored = AppViewModel(offline, cache = cache, dateProvider = { date })
        assertEquals(vm.state.value.lessons, restored.state.value.lessons)
        assertTrue(restored.state.value.scores.isNotEmpty())
        assertTrue(restored.state.value.examsLoaded)
        assertTrue(restored.state.value.onboarded)
        assertNull(restored.state.value.session)
        assertEquals(9, restored.state.value.week)
        restored.refresh()
        assertEquals(AppScreen.Home, restored.state.value.screen)
        assertTrue(restored.state.value.needsLogin)
    }

    @Test fun failedRefreshRetainsCachedResultsAndDoesNotOpenLogin() = runTest(dispatcher) {
        var failing = false
        val source = object : UrpScheduleDataSource by DemoUrpDataSource() {
            override suspend fun getSchedule(session: UserSession, term: Term): List<ScheduleLesson> =
                if (failing) throw UrpLoginException("expired") else DemoUrpDataSource().getSchedule(session, term)
        }
        val cache = AcademicCache()
        val vm = AppViewModel(source, cache = cache, dateProvider = { date })
        vm.loginWithSsoCookie("fixture=value"); advanceUntilIdle()
        val before = vm.state.value.lessons
        failing = true; vm.refresh(); advanceUntilIdle()
        assertEquals(before, vm.state.value.lessons)
        assertNull(vm.state.value.session)
        assertTrue(vm.state.value.needsLogin)
        assertEquals(AppScreen.Home, vm.state.value.screen)
        assertEquals(before, cache.read().snapshot.lessons)
    }

    @Test fun successfulEmptyScopeIsSavedAndClearRemovesPersonalData() = runTest(dispatcher) {
        val cache = AcademicCache()
        val source = object : UrpScheduleDataSource by DemoUrpDataSource() {
            override suspend fun getAllScores(session: UserSession): List<ScoreRecord> = emptyList()
        }
        val vm = AppViewModel(source, cache = cache, dateProvider = { date })
        vm.loginWithSsoCookie("fixture=value"); advanceUntilIdle()
        vm.setAllScoreTerms(true); vm.refresh(); advanceUntilIdle()
        assertTrue(vm.state.value.scoresLoaded)
        assertTrue(vm.state.value.scores.isEmpty())
        assertEquals("2026-10-28", cache.read().snapshot.allScoresUpdated)
        vm.setAllScoreTerms(false)
        assertTrue(vm.state.value.scores.isNotEmpty())
        vm.clearLocalData()
        assertTrue(cache.read().snapshot.lessons.isEmpty())
        assertTrue(cache.read().snapshot.termScores.isEmpty())
        assertNull(vm.state.value.session)
        assertEquals(AppScreen.Home, vm.state.value.screen)
    }

    @Test fun midnightChangesTodayWithoutChangingOfflineData() {
        var now = date
        val cache = AcademicCache()
        cache.write(AcademicSnapshot(semester = SemesterDates("2026-08-31", "2027-01-17")))
        val vm = AppViewModel(DemoUrpDataSource(), cache = cache, dateProvider = { now })
        now = LocalDate.parse("2026-11-02"); vm.updateLocalDate()
        assertEquals(now, vm.state.value.today)
        assertEquals(10, vm.state.value.week)
    }

    @Test fun unidentifiableSsoLoginIsolatedBeforeAnyNewDataLoads() = runTest(dispatcher) {
        val cache = AcademicCache()
        val saved = AcademicSnapshot(owner = "统一认证用户", lessons = listOf(
            ScheduleLesson("old", "old", "旧课表", "", "", 3, 1, 1, 1..20)), scheduleUpdated = "2026-10-27")
        cache.write(saved)
        val source = object : UrpScheduleDataSource by DemoUrpDataSource() {
            override suspend fun loginWithSsoCookie(account: String, cookie: String) = UserSession("统一认证用户", "测试用户", "fixture", ScheduleSource.URP)
            override suspend fun getSchedule(session: UserSession, term: Term): List<ScheduleLesson> = throw UrpLoginException("expired")
        }
        val vm = AppViewModel(source, cache = cache, dateProvider = { date })
        vm.loginWithSsoCookie("fixture=value"); advanceUntilIdle()
        assertTrue(vm.state.value.lessons.isEmpty())
        assertTrue(cache.read().snapshot.lessons.isEmpty())
        assertNull(vm.state.value.session)
        assertTrue(vm.state.value.needsLogin)
        assertTrue(vm.state.value.scores.isEmpty())
    }

    @Test fun invalidCalendarMetadataCannotEraseSuccessfulResults() = runTest(dispatcher) {
        val source = object : UrpScheduleDataSource by DemoUrpDataSource() {
            override suspend fun getSemesterDates(session: UserSession, date: LocalDate) = SemesterDates("bad-date", "bad-date")
        }
        val vm = AppViewModel(source, dateProvider = { date })
        vm.loginWithSsoCookie("fixture=value"); advanceUntilIdle()
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.lessons.isNotEmpty())
        assertNull(vm.state.value.semester)
    }

    @Test fun suppliedStudentIdSeparatesCachedAccounts() = runTest(dispatcher) {
        val cache = AcademicCache()
        cache.write(AcademicSnapshot(owner = "old-id", lessons = listOf(ScheduleLesson("old", "old", "旧课", "", "", 1, 1, 1, 1..20))))
        val vm = AppViewModel(object : UrpScheduleDataSource by DemoUrpDataSource() {
            override suspend fun loginWithSsoCookie(account: String, cookie: String) = UserSession(account, "用户", "fresh", ScheduleSource.URP)
        }, cache = cache, dateProvider = { date })
        vm.setAccount("new-id")
        vm.loginWithSsoCookie("fixture=value"); advanceUntilIdle()
        assertTrue(vm.state.value.lessons.none { it.id == "old" })
        assertEquals("new-id", cache.read().snapshot.owner)
    }
}
