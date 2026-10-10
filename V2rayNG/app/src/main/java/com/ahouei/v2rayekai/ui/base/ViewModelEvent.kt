package com.ahouei.v2rayekai.ui.base

/**
 * Base interface for ViewModel UI events.
 */
interface ViewModelEvent

/**
 * Common UI events for all ViewModels.
 */
interface BaseViewModelEvent : ViewModelEvent {
    object FinishActivity : BaseViewModelEvent
}
