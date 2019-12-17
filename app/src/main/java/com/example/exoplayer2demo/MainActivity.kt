package com.example.exoplayer2demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.exoplayer2demo.ExoPlayerView.Companion.ABR_ALGORITHM_DEFAULT
import com.example.exoplayer2demo.ExoPlayerView.Companion.ABR_ALGORITHM_EXTRA
import com.example.exoplayer2demo.ExoPlayerView.Companion.ACTION_VIEW
import com.example.exoplayer2demo.ExoPlayerView.Companion.EXTENSION_EXTRA
import com.example.exoplayer2demo.ExoPlayerView.Companion.PREFER_EXTENSION_DECODERS_EXTRA
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    // Sample videos, more can be found https://github.com/google/ExoPlayer/blob/release-v2/demos/main/src/main/assets/media.exolist.json
    private val hls = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"
    private val dash = "https://www.youtube.com/api/manifest/dash/id/3aa39fa2cc27967f/source/youtube?as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,source,id,as&ip=0.0.0.0&ipbits=0&expire=19000000000&signature=A2716F75795F5D2AF0E88962FFCD10DB79384F29.84308FF04844498CE6FBCE4731507882B8307798&key=ik0"
    private val mp4 = "https://html5demos.com/assets/dizzy.mp4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btn1.setOnClickListener { et_url.setText(hls) }
        btn2.setOnClickListener { et_url.setText(dash) }
        btn3.setOnClickListener { et_url.setText(mp4) }

        btn_play.setOnClickListener {
            val preferExtensionDecoders = false
            val intent = Intent(this, PlayerActivity::class.java)

            val videoUrl = et_url.text.toString()
            intent.putExtra(PREFER_EXTENSION_DECODERS_EXTRA, preferExtensionDecoders)
            intent.putExtra(ABR_ALGORITHM_EXTRA, ABR_ALGORITHM_DEFAULT)
            intent.data = Uri.parse(videoUrl)
            intent.action = ACTION_VIEW

            // this video url doesn't end with a file extension, therefore it needs a extension override
            // for buildMediaSource(Uri uri, @Nullable String overrideExtension) in the PlayerActivity
            if (videoUrl == dash) {
                intent.putExtra(EXTENSION_EXTRA, ".mpd")
            }

            startActivity(intent)
        }

        val l = arrayOf<Int>().map { it }.toMutableList()

    }
}
