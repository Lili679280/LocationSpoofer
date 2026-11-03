package com.vincenthzr.locationspoofer.viewmodel

import kotlinx.coroutines.flow.update

internal fun MainViewModel.setThemeMode(mode: Int) {
    settingsRepository.themeMode = mode
    _uiState.update { it.copy(themeMode = mode) }
}

internal fun MainViewModel.setFloatingBottomBar(enabled: Boolean) {
    settingsRepository.floatingBottomBar = enabled
    _uiState.update { it.copy(floatingBottomBar = enabled) }
}

internal fun MainViewModel.setLiquidGlass(enabled: Boolean) {
    settingsRepository.liquidGlass = enabled
    _uiState.update { it.copy(liquidGlass = enabled) }
}

internal fun MainViewModel.setLiquidGlassStrength(strength: Float) {
    settingsRepository.liquidGlassStrength = strength
    _uiState.update { it.copy(liquidGlassStrength = strength) }
}

internal fun MainViewModel.setPageScale(scale: Float) {
    settingsRepository.pageScale = scale
    _uiState.update { it.copy(pageScale = scale) }
}

internal fun MainViewModel.setPredictiveBack(enabled: Boolean) {
    settingsRepository.predictiveBack = enabled
    _uiState.update { it.copy(predictiveBack = enabled) }
}
