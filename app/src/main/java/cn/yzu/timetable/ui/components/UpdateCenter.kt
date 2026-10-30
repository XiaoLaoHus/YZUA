package cn.yzu.timetable.ui.components

import androidx.compose.runtime.mutableStateOf

/** 更新检查的进程级状态：开屏自动检查与“更多”中的手动检查共用。 */
object UpdateCenter {
    var checkedThisProcess = false
    val checking = mutableStateOf(false)
    val result = mutableStateOf<UpdateChecker.Result?>(null)
    val dismissed = mutableStateOf(false)

    suspend fun run(): UpdateChecker.Result {
        checking.value = true
        return try { UpdateChecker.check().also { result.value = it } } finally { checking.value = false }
    }
}
