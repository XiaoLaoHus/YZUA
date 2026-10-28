package cn.yzu.timetable.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun blankInputDoesNotLogin() { val vm = AppViewModel(); vm.login(); assertEquals("请输入学号和密码", vm.state.value.error) }
    @Test fun weekIsClamped() { val vm = AppViewModel(); repeat(3) { vm.shiftWeek(-1) }; assertEquals(1, vm.state.value.week); repeat(30) { vm.shiftWeek(1) }; assertEquals(20, vm.state.value.week) }
    @Test fun logoutResetsState() = runTest(dispatcher) {
        val vm = AppViewModel()
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
}
