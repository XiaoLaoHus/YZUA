package cn.yzu.timetable.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import cn.yzu.timetable.data.urp.DemoUrpDataSource
import cn.yzu.timetable.data.urp.UrpScheduleDataSource
import cn.yzu.timetable.data.model.*
import kotlinx.coroutines.CompletableDeferred

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun blankInputDoesNotLogin() { val vm = AppViewModel(DemoUrpDataSource()); vm.login(); assertEquals("请输入学号和密码", vm.state.value.error) }
    @Test fun weekIsClamped() { val vm = AppViewModel(DemoUrpDataSource()); repeat(3) { vm.shiftWeek(-1) }; assertEquals(1, vm.state.value.week); repeat(30) { vm.shiftWeek(1) }; assertEquals(20, vm.state.value.week) }
    @Test fun logoutResetsState() = runTest(dispatcher) {
        val vm = AppViewModel(DemoUrpDataSource())
        vm.setAccount("id")
        vm.setPassword("pw")
        vm.login()
        advanceUntilIdle()
        assertNotNull(vm.state.value.session)
        assertTrue(vm.state.value.lessons.isNotEmpty())
        assertEquals(AppScreen.Home, vm.state.value.screen)
        vm.logout()
        assertNull(vm.state.value.session)
        assertTrue(vm.state.value.lessons.isEmpty())
        assertEquals(AppScreen.Login, vm.state.value.screen)
    }

    @Test fun scoreFailureIsVisibleWhileOtherQueriesSucceed() = runTest(dispatcher) {
        val source = object : UrpScheduleDataSource by DemoUrpDataSource() {
            override suspend fun getScores(session: UserSession): List<ScoreRecord> = throw java.io.IOException("成绩查询不可用")
        }
        val vm = AppViewModel(source)
        vm.loginWithSsoCookie("fixture=test")
        advanceUntilIdle()
        assertEquals(AppScreen.Home, vm.state.value.screen)
        assertTrue(vm.state.value.lessons.isNotEmpty())
        assertEquals("成绩查询不可用", vm.state.value.scoreError)
        assertTrue(vm.state.value.examsLoaded)
        assertNull(vm.state.value.examError)
    }

    @Test fun logoutWhileLoginWaitsCannotRestorePreviousAccount() = runTest(dispatcher) {
        val waiting = CompletableDeferred<Unit>()
        val source = object : UrpScheduleDataSource by DemoUrpDataSource() {
            override suspend fun loginWithSsoCookie(account: String, cookie: String): UserSession {
                waiting.await()
                return UserSession("fixture", "测试用户", "test", ScheduleSource.DEMO)
            }
        }
        val vm = AppViewModel(source)
        vm.loginWithSsoCookie("fixture=test")
        runCurrent()
        vm.logout()
        waiting.complete(Unit)
        advanceUntilIdle()
        assertEquals(AppScreen.Login, vm.state.value.screen)
        assertNull(vm.state.value.session)
        assertTrue(vm.state.value.lessons.isEmpty())
    }

    @Test fun changingScoreScopeFetchesAllTermsAndClearsPreviousScopeResults() = runTest(dispatcher) {
        val source = object : UrpScheduleDataSource by DemoUrpDataSource() {
            override suspend fun getAllScores(session: UserSession): List<ScoreRecord> = emptyList()
        }
        val vm = AppViewModel(source)
        vm.loginWithSsoCookie("fixture=test")
        advanceUntilIdle()
        assertTrue(vm.state.value.scores.isNotEmpty())
        vm.setAllScoreTerms(true)
        advanceUntilIdle()
        assertTrue(vm.state.value.allScoreTerms)
        assertTrue(vm.state.value.scoresLoaded)
        assertTrue(vm.state.value.scores.isEmpty())
    }
}
