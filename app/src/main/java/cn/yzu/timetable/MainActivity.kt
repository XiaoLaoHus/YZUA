package cn.yzu.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.yzu.timetable.ui.AppRoot
import cn.yzu.timetable.ui.AppViewModel
import cn.yzu.timetable.ui.theme.YangzhouTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { YangzhouTheme { AppRoot(viewModel<AppViewModel>()) } } }
}
