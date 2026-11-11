package dev.shizzi

sealed interface ShizukuState {
    data object NotInstalled : ShizukuState
    data object NotRunning : ShizukuState
    data object PermissionRequired : ShizukuState
    data class Ready(val uid: Int, val isRoot: Boolean) : ShizukuState
}
