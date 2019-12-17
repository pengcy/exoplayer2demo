package com.example.exoplayer2demo

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.SimpleExoPlayer

/** An activity that plays media using [SimpleExoPlayer].  */
class PlayerActivity : AppCompatActivity() {

    companion object {
        // Saved instance state keys.
        private const val KEY_TRACK_SELECTOR_PARAMETERS = "track_selector_parameters"
        private const val KEY_WINDOW = "window"
        private const val KEY_POSITION = "position"
        private const val KEY_AUTO_PLAY = "auto_play"
    }

    private lateinit var exoPlayerView: ExoPlayerView

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.player_activity)

        val playerActivityRoot = findViewById<FrameLayout>(R.id.player_activity_root)

        exoPlayerView = ExoPlayerView(this)
        playerActivityRoot.addView(exoPlayerView)
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        exoPlayerView.releasePlayer()
        exoPlayerView.releaseAdsLoader()
        exoPlayerView.clearStartPosition()
        setIntent(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (grantResults.isEmpty()) {
            return
        }
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            exoPlayerView.initializePlayer()
        } else {
            exoPlayerView.showToast(R.string.storage_permission_denied)
            finish()
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        exoPlayerView.updateTrackSelectorParameters()
        exoPlayerView.updateStartPosition()
        outState.putParcelable(KEY_TRACK_SELECTOR_PARAMETERS, exoPlayerView.trackSelectorParameters)
        outState.putBoolean(KEY_AUTO_PLAY, exoPlayerView.isStartAutoPlay)
        outState.putInt(KEY_WINDOW, exoPlayerView.startWindow)
        outState.putLong(KEY_POSITION, exoPlayerView.startPosition)
    }

    // Activity input
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // See whether the player view wants to handle media or DPAD keys events.
        return exoPlayerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

}