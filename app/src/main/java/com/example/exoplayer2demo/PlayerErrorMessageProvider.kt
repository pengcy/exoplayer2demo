package com.example.exoplayer2demo

import android.content.Context
import android.util.Pair

import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil
import com.google.android.exoplayer2.util.ErrorMessageProvider

class PlayerErrorMessageProvider(private val context: Context) :
    ErrorMessageProvider<ExoPlaybackException> {

    override fun getErrorMessage(e: ExoPlaybackException): Pair<Int, String> {
        var errorString = context.getString(R.string.error_generic)
        if (e.type == ExoPlaybackException.TYPE_RENDERER) {
            val cause = e.rendererException
            if (cause is MediaCodecRenderer.DecoderInitializationException) {
                // Special case for decoder initialization failures.
                errorString = if (cause.decoderName == null) {
                    when {
                        cause.cause is MediaCodecUtil.DecoderQueryException -> context.getString(R.string.error_querying_decoders)
                        cause.secureDecoderRequired -> context.getString(R.string.error_no_secure_decoder, cause.mimeType)
                        else -> context.getString(R.string.error_no_decoder, cause.mimeType)
                    }
                } else {
                    context.getString(R.string.error_instantiating_decoder, cause.decoderName)
                }
            }
        }
        return Pair.create(0, errorString)
    }
}