Inculde exoplayer library as a dependency in the gradle file.
```
implementation 'com.google.android.exoplayer:exoplayer:2.X.X'
```

To initialize an instance of ExoPlayer. Context, RenderersFactory and TrackSelector are required, DrmSessionMannager can be null.
```
player = ExoPlayerFactory.newSimpleInstance(activity, renderersFactory, trackSelector!!, drmSessionManager)
```

To initialize the player with the media source. uri is the video resource link, extension is video type extension, it's needed only if the video link doesn't end with a normal regonizable extension such as .mp4, .m3u8, .mpd, etc.
```
val mediaSources = MediaSourceFactory.buildMediaSource(context, uri, extension)
player.prepare(mediaSource, !haveStartPosition, false)
```

To start:
```
player.playWhenReady = true
```

To pause:
```
player.playWhenReady = true
```

To stop:
```
player.stop()
```

To jump to a specific positon. It takes an integer representing a time duration in milliseconds, for example, this will bring the video at 30 seconds from the beginning.
```
player.seekTo(30*1000)
```

To select a track, an object of SelectionOverride is needed. The following is an example of selecting the first text track of a video. If the video does have a text track for closed caption, the following will show the closed caption.
```
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
```

To remove a track, simply call clearSelectionOverrides on the track selector's builder and disable it. Here is an example to turn off the closed caption by clearing the selection overrides for the text track and disable it.
```
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
```

More documentation can be found here: https://github.com/google/ExoPlayer
