package com.gridea.android.ui.screen.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首次启动引导页 ViewModel
 *
 * 通过 DataStore 记录引导完成状态（key: onboarding_completed）。
 * - 首次启动时 key 不存在，读取默认 false → 显示引导页
 * - 引导完成后写入 true → 后续启动直接进入主界面
 *
 * onboardingCompleted 取值：
 * - null：DataStore 读取中（加载态）
 * - false：未完成引导，需展示引导页
 * - true：已完成引导，进入主界面
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val onboardingCompleted: StateFlow<Boolean?> = dataStore.data
        .map { prefs -> prefs[ONBOARDING_COMPLETED] ?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * 标记引导已完成并持久化，下一次启动将直接进入主界面。
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[ONBOARDING_COMPLETED] = true
            }
        }
    }
}
