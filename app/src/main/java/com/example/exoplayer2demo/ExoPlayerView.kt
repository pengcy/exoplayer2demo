package com.example.exoplayer2demo

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.view.View.OnClickListener
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.exoplayer2demo.exoplayer2.VideoDownloadManager
import com.example.exoplayer2demo.exoplayer2.dialog.TrackSelectionDialog
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.drm.*
import com.google.android.exoplayer2.source.BehindLiveWindowException
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.source.ads.AdsLoader
import com.google.android.exoplayer2.source.ads.AdsMediaSource
import com.google.android.exoplayer2.trackselection.*
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import com.google.android.exoplayer2.ui.DebugTextViewHelper
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.util.Util
import com.google.gson.Gson
import kotlinx.android.synthetic.main.exoplayer_view.view.*
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.*
import kotlin.math.max

class ExoPlayerView : FrameLayout, OnClickListener, PlaybackPreparer, PlayerControlView.VisibilityListener {

    companion object {

        const val TAG = "ExoPlayerView"
        const val DRM_SCHEME_EXTRA = "drm_scheme"
        const val DRM_LICENSE_URL_EXTRA = "drm_license_url"
        const val DRM_KEY_REQUEST_PROPERTIES_EXTRA = "drm_key_request_properties"
        const val DRM_MULTI_SESSION_EXTRA = "drm_multi_session"
        const val PREFER_EXTENSION_DECODERS_EXTRA = "prefer_extension_decoders"
        const val ACTION_VIEW = "com.google.android.exoplayer.demo.action.VIEW"
        const val EXTENSION_EXTRA = "extension"
        const val ACTION_VIEW_LIST = "com.google.android.exoplayer.demo.action.VIEW_LIST"
        const val URI_LIST_EXTRA = "uri_list"
        const val EXTENSION_LIST_EXTRA = "extension_list"
        const val AD_TAG_URI_EXTRA = "ad_tag_uri"
        const val ABR_ALGORITHM_EXTRA = "abr_algorithm"
        const val ABR_ALGORITHM_DEFAULT = "default"
        const val ABR_ALGORITHM_RANDOM = "random"

        // For backwards compatibility only.
        private const val DRM_SCHEME_UUID_EXTRA = "drm_scheme_uuid"

        private val DEFAULT_COOKIE_MANAGER: CookieManager = CookieManager()

        init {
            DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER)
        }

        private fun isBehindLiveWindow(e: ExoPlaybackException): Boolean {
            if (e.type != ExoPlaybackException.TYPE_SOURCE) {
                return false
            }
            var cause: Throwable? = e.sourceException
            while (cause != null) {
                if (cause is BehindLiveWindowException) {
                    return true
                }
                cause = cause.cause
            }
            return false
        }
    }

    private var isShowingTrackSelectionDialog: Boolean = false
    private var player: SimpleExoPlayer? = null
    private var mediaDrm: FrameworkMediaDrm? = null
    private var mediaSource: MediaSource? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var debugViewHelper: DebugTextViewHelper? = null
    private var lastSeenTrackGroupArray: TrackGroupArray? = null

    var trackSelectorParameters: DefaultTrackSelector.Parameters? = null
        private set

    var isStartAutoPlay: Boolean = false
        private set

    var startWindow: Int = 0
        private set

    var startPosition: Long = 0
        private set

    // Fields used only for ad playback. The ads loader is loaded via reflection.
    private var adsLoader: AdsLoader? = null
    private var loadedAdTagUri: Uri? = null
    private lateinit var activity: FragmentActivity

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context)
    }

    private fun initDebugViews() {
        log_tracks.setOnClickListener {
            val mappedTrackInfo = Assertions.checkNotNull(trackSelector!!.currentMappedTrackInfo)
            val parameters = trackSelector!!.parameters

            for (rendererIndex in 0 until mappedTrackInfo.rendererCount) {
                if (TrackSelectionDialog.showTabForRenderer(mappedTrackInfo, rendererIndex)) {
                    val trackType = mappedTrackInfo.getRendererType(rendererIndex)
                    val trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex)
                    val isRendererDisabled = parameters.getRendererDisabled(rendererIndex)
                    val selectionOverride = parameters.getSelectionOverride(rendererIndex, trackGroupArray)

                    Log.d(TAG, "------------------------------------------------------rendererIndex $rendererIndex")
                    Log.d(TAG, "track type: " + trackTypeToName(trackType))
                    Log.d(TAG, "track group array: " + Gson().toJson(trackGroupArray))
                    for (groupIndex in 0 until trackGroupArray.length) {
                        for (trackIndex in 0 until trackGroupArray.get(groupIndex).length) {
                            val trackName = DefaultTrackNameProvider(resources).getTrackName(trackGroupArray.get(groupIndex).getFormat(trackIndex))
                            val isTrackSupported = mappedTrackInfo.getTrackSupport(rendererIndex, groupIndex, trackIndex) == RendererCapabilities.FORMAT_HANDLED
                            Log.d(TAG,"groupIndex $groupIndex: trackName: $trackName, isTrackSupported: $isTrackSupported")
                        }
                    }
                    Log.d(TAG, "isRendererDisabled: $isRendererDisabled")
                    Log.d(TAG, "selectionOverride: " + Gson().toJson(selectionOverride))
                }
            }
        }

        show_text_caption.setOnClickListener {
            val mappedTrackInfo = Assertions.checkNotNull(trackSelector!!.currentMappedTrackInfo)
            val parameters = trackSelector!!.parameters
            val builder = parameters.buildUpon()
            for (rendererIndex in 0 until mappedTrackInfo.rendererCount) {
                val trackType = mappedTrackInfo.getRendererType(rendererIndex)
                if (trackType == C.TRACK_TYPE_TEXT && TrackSelectionDialog.showTabForRenderer(mappedTrackInfo, rendererIndex)) {
                    builder.clearSelectionOverrides(rendererIndex).setRendererDisabled(rendererIndex, false)
                    //{"data":0,"groupIndex":1,"length":1,"reason":2,"tracks":[0]}
                    // for demo purpose, hardcoding to pick the first group and first track
                    val groupIndex = 0
                    val tracks = intArrayOf(0)
                    val reason = 2
                    val data = 0
                    val override = DefaultTrackSelector.SelectionOverride(groupIndex, tracks, reason, data)

                    builder.setSelectionOverride(
                        rendererIndex,
                        mappedTrackInfo.getTrackGroups(rendererIndex),
                        override
                    )
                }
            }

            trackSelector!!.setParameters(builder)
        }

        remove_text_caption.setOnClickListener {
            val mappedTrackInfo = Assertions.checkNotNull(trackSelector!!.currentMappedTrackInfo)
            val parameters = trackSelector!!.parameters
            val builder = parameters.buildUpon()
            for (rendererIndex in 0 until mappedTrackInfo.rendererCount) {
                val trackType = mappedTrackInfo.getRendererType(rendererIndex)
                if (trackType == C.TRACK_TYPE_TEXT) {
                    builder.clearSelectionOverrides(rendererIndex)
                        .setRendererDisabled(rendererIndex, true)
                }
            }
            trackSelector!!.setParameters(builder)
        }

    }

    private fun init(context: Context) {
        activity = context as FragmentActivity
        registerLifecycleCallbacks()

        val layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        layoutParams.gravity = Gravity.BOTTOM
        setLayoutParams(layoutParams)

        val inflate = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflate.inflate(R.layout.exoplayer_view, this)

        select_tracks_button.setOnClickListener(this)
        initDebugViews()
        player_view.setControllerVisibilityListener(this)
        player_view.setErrorMessageProvider(PlayerErrorMessageProvider(context.getApplicationContext()))
        player_view.requestFocus()

        trackSelectorParameters = DefaultTrackSelector.ParametersBuilder().build()
        clearStartPosition()
    }

    private fun trackTypeToName(trackType: Int): String {
        return when (trackType) {
            C.TRACK_TYPE_VIDEO -> "TRACK_TYPE_VIDEO"
            C.TRACK_TYPE_AUDIO -> "TRACK_TYPE_AUDIO"
            C.TRACK_TYPE_TEXT -> "TRACK_TYPE_TEXT"
            else -> "Invalid track type"
        }
    }

    // Activity input
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // See whether the player view wants to handle media or DPAD keys events.
        return player_view.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    //region OnClickListener methods
    override fun onClick(view: View) {
        if (view === select_tracks_button && !isShowingTrackSelectionDialog && TrackSelectionDialog.willHaveContent(trackSelector!!)) {
            isShowingTrackSelectionDialog = true
            val trackSelectionDialog = TrackSelectionDialog.createForTrackSelector(trackSelector!!) {
                isShowingTrackSelectionDialog = false
            }
            trackSelectionDialog.show(activity.supportFragmentManager, null)
        }
    }
    //endregion

    //region PlaybackControlView.PlaybackPreparer implementation
    override fun preparePlayback() {
        player!!.retry()
    }
    //endregion

    //region PlaybackControlView.VisibilityListener implementation
    override fun onVisibilityChange(visibility: Int) {
        controls_root.visibility = visibility
    }
    //endregion

    fun initializePlayer() {
        if (player == null) {
            val intent = activity.intent
            val action = intent.action
            var uris: Array<Uri?> = emptyArray()
            var extensions: Array<String?>? = emptyArray()
            when {
                ACTION_VIEW == action -> {
                    uris = arrayOf(intent.data)
                    extensions = arrayOf(intent.getStringExtra(EXTENSION_EXTRA))
                }
                ACTION_VIEW_LIST == action -> intent.getStringArrayExtra(URI_LIST_EXTRA)?.let { uriStrings ->
                    uris = arrayOfNulls(uriStrings.size)
                    for (i in uriStrings.indices) {
                        uris[i] = Uri.parse(uriStrings[i])
                    }
                    extensions = intent.getStringArrayExtra(EXTENSION_LIST_EXTRA)
                    if (extensions == null) {
                        extensions = arrayOfNulls(uriStrings.size)
                    }
                }
                else -> {
                    showToast(activity.getString(R.string.unexpected_intent_action, action))
                    activity.finish()
                    return
                }
            }
            if (!Util.checkCleartextTrafficPermitted(*uris)) {
                showToast(R.string.error_cleartext_not_permitted)
                return
            }
            if (Util.maybeRequestReadExternalStoragePermission(/* activity= */activity, *uris)) {
                // The player will be reinitialized if the permission is granted.
                return
            }

            var drmSessionManager: DefaultDrmSessionManager<FrameworkMediaCrypto>? = null
            if (intent.hasExtra(DRM_SCHEME_EXTRA) || intent.hasExtra(DRM_SCHEME_UUID_EXTRA)) {
                val drmLicenseUrl = intent.getStringExtra(DRM_LICENSE_URL_EXTRA)
                val keyRequestPropertiesArray = intent.getStringArrayExtra(DRM_KEY_REQUEST_PROPERTIES_EXTRA)
                val multiSession = intent.getBooleanExtra(DRM_MULTI_SESSION_EXTRA, false)
                var errorStringId = R.string.error_drm_unknown
                try {
                    val drmSchemeExtra = if (intent.hasExtra(DRM_SCHEME_EXTRA)) DRM_SCHEME_EXTRA else DRM_SCHEME_UUID_EXTRA
                    val drmSchemeUuid = Util.getDrmUuid(intent.getStringExtra(drmSchemeExtra))
                    if (drmSchemeUuid == null) {
                        errorStringId = R.string.error_drm_unsupported_scheme
                    } else {
                        drmSessionManager = buildDrmSessionManagerV18(
                            drmSchemeUuid,
                            drmLicenseUrl,
                            keyRequestPropertiesArray,
                            multiSession
                        )
                    }
                } catch (e: UnsupportedDrmException) {
                    errorStringId =
                        if (e.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME)
                            R.string.error_drm_unsupported_scheme
                        else
                            R.string.error_drm_unknown
                }
                if (drmSessionManager == null) {
                    showToast(errorStringId)
                    activity.finish()
                    return
                }
            }

            val trackSelectionFactory: TrackSelection.Factory
            val abrAlgorithm = intent.getStringExtra(ABR_ALGORITHM_EXTRA)
            trackSelectionFactory = if (abrAlgorithm == null || ABR_ALGORITHM_DEFAULT == abrAlgorithm) {
                AdaptiveTrackSelection.Factory()
            } else if (ABR_ALGORITHM_RANDOM == abrAlgorithm) {
                RandomTrackSelection.Factory()
            } else {
                showToast(R.string.error_unrecognized_abr_algorithm)
                activity.finish()
                return
            }

            val preferExtensionDecoders = intent.getBooleanExtra(PREFER_EXTENSION_DECODERS_EXTRA, false)
            val renderersFactory = VideoDownloadManager.getInstance(activity.applicationContext).buildRenderersFactory(preferExtensionDecoders)

            trackSelector = DefaultTrackSelector(trackSelectionFactory)
            trackSelector!!.parameters = trackSelectorParameters!!
            lastSeenTrackGroupArray = null

            player = ExoPlayerFactory.newSimpleInstance(activity, renderersFactory, trackSelector!!, drmSessionManager)
            player!!.addListener(PlayerEventListener())
            player!!.playWhenReady = isStartAutoPlay
            player!!.addAnalyticsListener(EventLogger(trackSelector))
            player!!.stop()
            player_view.player = player
            player_view.setPlaybackPreparer(this)
            debugViewHelper = DebugTextViewHelper(player!!, debug_text_view)
            debugViewHelper!!.start()

            val mediaSources = arrayOfNulls<MediaSource>(uris.size)
            for (i in uris.indices) {
                mediaSources[i] = MediaSourceFactory.buildMediaSource(
                    activity.applicationContext,
                    uris[i],
                    extensions?.get(i)
                )
            }
            mediaSource = if (mediaSources.size == 1) mediaSources[0] else ConcatenatingMediaSource(*mediaSources)
            val adTagUriString = intent.getStringExtra(AD_TAG_URI_EXTRA)
            if (adTagUriString != null) {
                val adTagUri = Uri.parse(adTagUriString)
                if (adTagUri != loadedAdTagUri) {
                    releaseAdsLoader()
                    loadedAdTagUri = adTagUri
                }
                val adsMediaSource = createAdsMediaSource(mediaSource!!, Uri.parse(adTagUriString))
                if (adsMediaSource != null) {
                    mediaSource = adsMediaSource
                } else {
                    showToast(R.string.ima_not_loaded)
                }
            } else {
                releaseAdsLoader()
            }
        }
        val haveStartPosition = startWindow != C.INDEX_UNSET
        if (haveStartPosition) {
            player!!.seekTo(startWindow, startPosition)
        }
        player!!.prepare(mediaSource!!, !haveStartPosition, false)
        updateButtonVisibility()
    }

    @Throws(UnsupportedDrmException::class)
    private fun buildDrmSessionManagerV18(uuid: UUID, licenseUrl: String?, keyRequestPropertiesArray: Array<String>?, multiSession: Boolean): DefaultDrmSessionManager<FrameworkMediaCrypto> {
        val licenseDataSourceFactory = VideoDownloadManager.getInstance(activity.applicationContext).buildHttpDataSourceFactory()
        val drmCallback = HttpMediaDrmCallback(licenseUrl, licenseDataSourceFactory)
        if (keyRequestPropertiesArray != null) {
            var i = 0
            while (i < keyRequestPropertiesArray.size - 1) {
                drmCallback.setKeyRequestProperty(keyRequestPropertiesArray[i], keyRequestPropertiesArray[i + 1])
                i += 2
            }
        }
        releaseMediaDrm()
        mediaDrm = FrameworkMediaDrm.newInstance(uuid)
        return DefaultDrmSessionManager(uuid, mediaDrm!!, drmCallback, null, multiSession)
    }

    fun releasePlayer() {
        if (player != null) {
            updateTrackSelectorParameters()
            updateStartPosition()
            debugViewHelper!!.stop()
            debugViewHelper = null
            player!!.release()
            player = null
            mediaSource = null
            trackSelector = null
        }
        if (adsLoader != null) {
            adsLoader!!.setPlayer(null)
        }
        releaseMediaDrm()
    }

    private fun releaseMediaDrm() {
        if (mediaDrm != null) {
            mediaDrm!!.release()
            mediaDrm = null
        }
    }

    fun releaseAdsLoader() {
        if (adsLoader != null) {
            adsLoader!!.release()
            adsLoader = null
            loadedAdTagUri = null
            player_view.overlayFrameLayout!!.removeAllViews()
        }
    }

    fun updateTrackSelectorParameters() {
        if (trackSelector != null) {
            trackSelectorParameters = trackSelector!!.parameters
        }
    }

    fun updateStartPosition() {
        if (player != null) {
            isStartAutoPlay = player!!.playWhenReady
            startWindow = player!!.currentWindowIndex
            startPosition = max(0, player!!.contentPosition)
        }
    }

    fun clearStartPosition() {
        isStartAutoPlay = true
        startWindow = C.INDEX_UNSET
        startPosition = C.TIME_UNSET
    }

    /** Returns an ads media source, reusing the ads loader if one exists.  */
    private fun createAdsMediaSource(mediaSource: MediaSource, adTagUri: Uri): MediaSource? {
        // Load the extension source using reflection so the demo app doesn't have to depend on it.
        // The ads loader is reused for multiple playbacks, so that ad playback can resume.
        try {
            val loaderClass = Class.forName("com.google.android.exoplayer2.ext.ima.ImaAdsLoader")
            if (adsLoader == null) {
                // Full class names used so the LINT.IfChange rule triggers should any of the classes move.
                // LINT.IfChange
                val loaderConstructor = loaderClass
                    .asSubclass(AdsLoader::class.java)
                    .getConstructor(android.content.Context::class.java, Uri::class.java)
                // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
                adsLoader = loaderConstructor.newInstance(this, adTagUri)
            }
            adsLoader!!.setPlayer(player)
            val adMediaSourceFactory = object : AdsMediaSource.MediaSourceFactory {
                override fun createMediaSource(uri: Uri): MediaSource {
                    return MediaSourceFactory.buildMediaSource(activity.applicationContext, uri)
                }

                override fun getSupportedTypes(): IntArray {
                    return intArrayOf(C.TYPE_DASH, C.TYPE_SS, C.TYPE_HLS, C.TYPE_OTHER)
                }
            }
            return AdsMediaSource(mediaSource, adMediaSourceFactory, adsLoader!!, player_view)
        } catch (e: ClassNotFoundException) {
            // IMA extension not loaded.
            return null
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    }

    //region user controls
    private fun updateButtonVisibility() {
        if (player != null && TrackSelectionDialog.willHaveContent(trackSelector!!)) {
            select_tracks_button.isEnabled = true
            log_tracks.isEnabled = true
            show_text_caption.isEnabled = true
            remove_text_caption.isEnabled = true
        } else {
            select_tracks_button.isEnabled = false
            log_tracks.isEnabled = false
            show_text_caption.isEnabled = false
            remove_text_caption.isEnabled = false
        }
    }

    private fun showControls() {
        controls_root.visibility = View.VISIBLE
    }

    fun showToast(messageId: Int) {
        showToast(activity.getString(messageId))
    }

    private fun showToast(message: String) {
        Toast.makeText(activity.applicationContext, message, Toast.LENGTH_LONG).show()
    }
    //endregion

    //region Player.EventListener
    private inner class PlayerEventListener : Player.EventListener {

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                showControls()
            }
            updateButtonVisibility()
        }

        override fun onPlayerError(e: ExoPlaybackException?) {
            if (isBehindLiveWindow(e!!)) {
                clearStartPosition()
                initializePlayer()
            } else {
                updateButtonVisibility()
                showControls()
            }
        }

        override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {
            updateButtonVisibility()
            if (trackGroups !== lastSeenTrackGroupArray) {
                val mappedTrackInfo = trackSelector!!.currentMappedTrackInfo
                if (mappedTrackInfo != null) {
                    if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO) == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
                        showToast(R.string.error_unsupported_video)
                    }
                    if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_AUDIO) == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
                        showToast(R.string.error_unsupported_audio)
                    }
                }
                lastSeenTrackGroupArray = trackGroups
            }
        }
    }
    //endregion

    //region activity lifecycle callbacks
    private fun registerLifecycleCallbacks() { // using registerActivityLifecycleCallbacks makes it easier to integrate player
        val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, bundle: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                if (activity === this@ExoPlayerView.activity) {
                    if (Util.SDK_INT > 23) {
                        initializePlayer()
                        if (player_view != null) {
                            player_view.onResume()
                        }
                    }
                }
            }

            override fun onActivityResumed(activity: Activity) {
                if (activity === this@ExoPlayerView.activity) {
                    if (Util.SDK_INT <= 23 || player == null) {
                        initializePlayer()
                        if (player_view != null) {
                            player_view.onResume()
                        }
                    }
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (activity === this@ExoPlayerView.activity) {
                    if (Util.SDK_INT <= 23) {
                        if (player_view != null) {
                            player_view.onPause()
                        }
                        releasePlayer()
                    }
                }
            }

            override fun onActivityStopped(activity: Activity) {
                if (activity === this@ExoPlayerView.activity) {
                    if (Util.SDK_INT > 23) {
                        if (player_view != null) {
                            player_view.onPause()
                        }
                        releasePlayer()
                    }
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {

            }

            override fun onActivityDestroyed(activity: Activity) {
                if (activity === this@ExoPlayerView.activity) {
                    releaseAdsLoader()
                }
            }
        }


        activity.application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }
    //endregion

}