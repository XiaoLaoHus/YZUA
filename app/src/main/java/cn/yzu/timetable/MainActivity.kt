package cn.yzu.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import cn.yzu.timetable.ui.AppRoot
import cn.yzu.timetable.ui.AppViewModel
import cn.yzu.timetable.ui.theme.YangzhouTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = cn.yzu.timetable.ui.AppViewModelFactory(
            cn.yzu.timetable.data.AcademicCache(java.io.File(noBackupFilesDir, "academic-v1.json")))
        val model = androidx.lifecycle.ViewModelProvider(this, factory)[AppViewModel::class.java]
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) { model.updateLocalDate(); delay(60_000) }
            }
        }
        setContent { YangzhouTheme { AppRoot(model) } }
    }
}
