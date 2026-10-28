package cn.yzu.timetable.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AppViewModelFactory(private val cache: cn.yzu.timetable.data.AcademicCache) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AppViewModel::class.java))
        return AppViewModel(cache = cache) as T
    }
}
