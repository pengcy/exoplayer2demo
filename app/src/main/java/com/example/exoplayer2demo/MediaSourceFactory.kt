package com.example.exoplayer2demo

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.exoplayer2demo.exoplayer2.VideoDownloadManager
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.offline.DownloadHelper
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource
import com.google.android.exoplayer2.util.Util

object MediaSourceFactory {
    private const val tag = "MediaSourceFactory"

    @JvmOverloads
    fun buildMediaSource(context: Context, uri: Uri?, overrideExtension: String? = null): MediaSource {
        Log.d(tag, "buildMediaSource uri: $uri overrideExtension: $overrideExtension")

        val dataSourceFactory =
            VideoDownloadManager.getInstance(context.applicationContext).buildDataSourceFactory()

        val downloadRequest = VideoDownloadManager.getInstance(context.applicationContext).downloadTracker.getDownloadRequest(uri)
        if (downloadRequest != null) {
            return DownloadHelper.createMediaSource(downloadRequest, dataSourceFactory)
        }
        when (@C.ContentType val type = Util.inferContentType(uri, overrideExtension)) {
            C.TYPE_DASH -> {
                Log.d(tag, "buildMediaSource TYPE_DASH: TYPE_DASH")
                return DashMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
            }
            C.TYPE_SS -> {
                Log.d(tag, "buildMediaSource TYPE_DASH: TYPE_SS")
                return SsMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
            }
            C.TYPE_HLS -> {
                Log.d(tag, "buildMediaSource TYPE_DASH: TYPE_HLS")
                return HlsMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
            }
            C.TYPE_OTHER -> {
                Log.d(tag, "buildMediaSource TYPE_DASH: TYPE_OTHER")
                return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
            }
            else -> throw IllegalStateException("Unsupported type: $type")
        }
    }
}
