package dev.personal.galleryorganizer

import dev.personal.galleryorganizer.bridge.GalleryHostApi
import dev.personal.galleryorganizer.bridge.GalleryHostApiImpl
import dev.personal.galleryorganizer.bridge.ProgressStreamHandler
import dev.personal.galleryorganizer.bridge.ScanProgressStreamHandler
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    private var progressHandler: ProgressStreamHandler? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        GalleryHostApi.setUp(messenger, GalleryHostApiImpl(this))
        val handler = ProgressStreamHandler()
        ScanProgressStreamHandler.register(messenger, handler)
        progressHandler = handler
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        GalleryHostApi.setUp(flutterEngine.dartExecutor.binaryMessenger, null)
        progressHandler?.dispose()
        progressHandler = null
        super.cleanUpFlutterEngine(flutterEngine)
    }
}
