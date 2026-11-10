package dev.personal.galleryorganizer.scan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-wide latest progress; null until a scan ran in this process. */
object ScanProgressBus {
    private val mutable = MutableStateFlow<ScanProgress?>(null)
    val state: StateFlow<ScanProgress?> = mutable

    fun publish(progress: ScanProgress) {
        mutable.value = progress
    }
}
