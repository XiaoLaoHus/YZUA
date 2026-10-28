package cn.yzu.timetable.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AppViewModelTest {
    @Test fun blankInputDoesNotLogin() { val vm = AppViewModel(); vm.login(); assertEquals("请输入学号和密码", vm.state.value.error) }
    @Test fun weekIsClamped() { val vm = AppViewModel(); repeat(3) { vm.shiftWeek(-1) }; assertEquals(1, vm.state.value.week); repeat(30) { vm.shiftWeek(1) }; assertEquals(20, vm.state.value.week) }
    @Test fun logoutResetsState() = runTest(Dispatchers.Unconfined) { val vm = AppViewModel(); vm.setAccount("id"); vm.setPassword("pw"); vm.login(); kotlinx.coroutines.test.advanceUntilIdle(); vm.logout(); assertTrue(vm.state.value.session == null); assertTrue(vm.state.value.lessons.isEmpty()) }
}
