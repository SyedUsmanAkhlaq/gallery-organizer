package dev.personal.galleryorganizer.bridge

import dev.personal.galleryorganizer.scan.ScanProgressBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Streams [ScanProgressBus] to Flutter (Pigeon-generated EventChannel). */
class ProgressStreamHandler : ScanProgressStreamHandler() {
    private val scope = MainScope()
    private var job: Job? = null

    override fun onListen(p0: Any?, sink: PigeonEventSink<ScanProgressMsg>) {
        job?.cancel()
        job = scope.launch {
            ScanProgressBus.state.filterNotNull().collect { sink.success(it.toMsg()) }
        }
    }

    override fun onCancel(p0: Any?) {
        job?.cancel()
        job = null
    }

    fun dispose() = scope.cancel()
}
