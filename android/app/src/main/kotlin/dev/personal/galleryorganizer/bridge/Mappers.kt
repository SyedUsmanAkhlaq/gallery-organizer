package dev.personal.galleryorganizer.bridge

import dev.personal.galleryorganizer.diag.ErrorEntry
import dev.personal.galleryorganizer.scan.ScanProgress
import dev.personal.galleryorganizer.scan.ScanStateK
import dev.personal.galleryorganizer.scan.StageK

// Maps internal (Android-free, unit-tested) types to Pigeon-generated ones.

fun ScanStateK.toMsg(): ScanState = when (this) {
    ScanStateK.IDLE -> ScanState.IDLE
    ScanStateK.RUNNING -> ScanState.RUNNING
    ScanStateK.PAUSED -> ScanState.PAUSED
    ScanStateK.COMPLETED -> ScanState.COMPLETED
    ScanStateK.FAILED -> ScanState.FAILED
}

fun StageK.toMsg(): ScanStage = when (this) {
    StageK.INDEXING -> ScanStage.INDEXING
    StageK.CLOTHING -> ScanStage.CLOTHING
    StageK.FACES -> ScanStage.FACES
}

fun ScanProgress.toMsg() = ScanProgressMsg(
    state = state.toMsg(),
    stage = stage.toMsg(),
    scanned = scanned.toLong(),
    total = total.toLong(),
    currentAssetId = currentAssetId?.toString(),
    avgMsPerImage = avgMsPerImage,
    message = message,
)

fun ErrorEntry.toMsg() = ErrorEntryMsg(
    timestampMillis = timestampMillis,
    source = source,
    message = message,
    stack = stack,
)
