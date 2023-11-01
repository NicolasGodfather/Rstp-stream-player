package com.example.rtsp

import android.app.Activity
import android.media.MediaCodecList
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.Surface
import com.example.rtsp.FrameQueue.FrameQueueCallback
import com.example.rtsp.PlayerProtocol.PlaybackState.*
import com.example.rtsp.VideoDecodeThread.VideoDecoderCallback
import java.io.IOException
import java.net.*
import java.util.concurrent.atomic.*

const val MAX_COUNTER = 50

class RtspThread(
	val activity: Activity,
	val actionRestartRtsp: () -> Unit,
	val actionRtspStateChanged: (Int, Any?) -> Unit? = { it1, it2 -> },
	val actionShowEmptyScreen: (Boolean) -> Unit? = {}
): Thread(), FrameQueueCallback, VideoDecoderCallback {

	var videoDecodeThread: VideoDecodeThread? = null
	var audioDecodeThread: AudioDecodeThread? = null
	private var videoMimeType: String = "video/avc"
	var surfaceWidth: Int = 1920
	var surfaceHeight: Int = 1080
	private var videoFrameQueue: FrameQueue = FrameQueue(this)
	private var audioFrameQueue: FrameQueue = FrameQueue(this)
	private val DEFAULT_RTSP_PORT = 554
	private var audioMimeType: String = "audio/mp4a-latm"
	private var audioSampleRateHz: Int = 0
	private var audioChannelCount: Int = 0
	private var audioCodecConfig: ByteArray? = null
	var isTimeout = true
	var videoUrl = ""
	var surface: Surface? = null
		set(value) {
			Log.d("RtspThread", "surface was set in THREAD")
			field = value
		}
	var counterError = 0
	var rtspStopped: AtomicBoolean = AtomicBoolean(false)
//	var rtspClient: RtspClient? = null
//
//	 now pausing/playing doesn't support on ms
//	fun pauseDecoding() {
//		rtspClient?.pause()
//		videoDecodeThread?.pauseDecoding()
//		audioDecodeThread?.pauseDecoding()
//	}
//   now pausing/playing doesn't support on ms
//	fun resumeDecoding() {
//		rtspClient?.play()
//		videoDecodeThread?.resumeDecoding()
//		audioDecodeThread?.resumeDecoding()
//	}

	private val proxyClientListener = object: RtspClient.RtspClientListener {
		override fun onRtspDisconnected() {
			Log.d("RtspThread", "onRtspDisconnected()")
//			actionRtspStateChanged.invoke(STATE_RELEASED.state, null)
			// todo handle status when set speed
		}

		override fun onRtspFailed(message: String?) {
			Log.d("RtspThread", "onRtspFailed(message=\"$message\")")
			showError(needShow = true)
		}

		override fun onRtspFailedStatus204() {
			activity.runOnUiThread {
//				showToast("Status code 204\nArchive not exist", activity)
				actionRtspStateChanged.invoke(STATE_ERROR_OCCURRED.state, RtspError204())
			}
			Log.d("RtspThread", "onRtspFailedStatus204")
			showError(needShow = true)
		}

		override fun onRtspConnected(sdpInfo: RtspClient.SdpInfo) {
			showError(needShow = false)
			Log.d("RtspThread", "onRtspConnected() time = ${sdpInfo.time}")
			actionRtspStateChanged.invoke(STATE_STARTED.state, sdpInfo.time)
			if (sdpInfo.videoTrack != null) {
				videoFrameQueue.clear()
				when (sdpInfo.videoTrack?.videoCodec) {
					RtspClient.VIDEO_CODEC_H264 -> videoMimeType = "video/avc"
					RtspClient.VIDEO_CODEC_H265 -> videoMimeType = "video/hevc"
				}
				val sps: ByteArray? = sdpInfo.videoTrack?.sps
				val pps: ByteArray? = sdpInfo.videoTrack?.pps
				// Initialize decoder

				if (sps != null && pps != null) {
					val data = ByteArray(sps.size + pps.size)
					sps.copyInto(data, 0, 0, sps.size)
					pps.copyInto(data, sps.size, 0, pps.size)
					videoFrameQueue.push(FrameQueue.Frame(data, 0, data.size, 0))
				} else {
					Log.d("RtspThread", "RTSP SPS and PPS NAL units missed in SDP")
				}
			}
			if (sdpInfo.audioTrack != null) {
				audioFrameQueue.clear()
				audioSampleRateHz = sdpInfo.audioTrack?.sampleRateHz!!
				audioChannelCount = sdpInfo.audioTrack?.channels!!
				audioCodecConfig = sdpInfo.audioTrack?.config
				Log.d(
					"RtspThread",
					"audioCodecConfig = $audioCodecConfig size = ${audioCodecConfig?.size}, audioChannelCount = $audioChannelCount, audioSampleRate = $audioSampleRateHz"
				)
			}
			onRtspClientConnected()
		}

		override fun onRtspFailedUnauthorized() {
			Log.d(
				"RtspThread",
				"onRtspFailedUnauthorized() - RTSP username or password is incorrect"
			)
			showError(needShow = true)
		}

		override fun onRtspVideoNalUnitReceived(
			data: ByteArray,
			offset: Int,
			length: Int,
			timestamp: Long
		) {
			showError(needShow = false)
			if (length > 0)
				videoFrameQueue.push(FrameQueue.Frame(data, offset, length, timestamp))
		}

		override fun onRtspAudioSampleReceived(
			data: ByteArray,
			offset: Int,
			length: Int,
			timestamp: Long
		) {
			if (length > 0) {
//					Log.d ( "RtspThread",onRtspAudioSampleReceived(length=$length, timestamp=$timestamp)" )
				// sampleRate: 48000, bufferSize: 7696
				audioFrameQueue.push(FrameQueue.Frame(data, offset, length, timestamp))
			}
		}

		override fun onRtspConnecting() {
			Log.d("RtspThread", "RTSP connecting")
		}

		override fun onRtspFailedSocketException(message: String?) {
			Log.d("RtspThread", "RTSP onRtspFailedSocketException(message=\"$message\")")
			actionRestartRtsp.invoke()
		}
	}

	init {
		Log.d("RtspThread", "init THREAD")
	}

	fun onRtspClientReleased() {
		rtspStopped.set(true)
//		surface = null
		stopDecoders()
		Log.d("RtspThread", "onRtspClientReleased() - stop RTSP")
//		// Wake up sleep() code
		interrupt()
	}

	fun stopDecoders() {
		Log.d("RtspThread", "stopDecoders()")
		stopVideo()
		stopAudio()
	}

	fun stopVideo() {
		videoDecodeThread?.stopAsync()
		videoDecodeThread = null
	}

	fun stopAudio() {
		audioDecodeThread?.stopAsync()
		audioDecodeThread = null
	}

	fun playVideo() {
		if (surface != null) {
			videoDecodeThread = VideoDecodeThread(
				surface,
				videoMimeType,
				surfaceWidth,
				surfaceHeight,
				videoFrameQueue,
				this,
				{
					actionRtspStateChanged.invoke(STATE_PLAYING.state, it)
				}, {
					actionRtspStateChanged.invoke(STATE_TIMELINE_CHANGED.state, null)
				})
			videoDecodeThread?.start()
		}
	}

	fun playAudio() {
		Log.d(
			"RtspThread",
			"audioMimeType: $audioMimeType audioSampleRate: $audioSampleRateHz, audioChannelCount: $audioChannelCount"
		)
		// check has audio stream or not
		if (audioChannelCount > 0 && audioSampleRateHz > 0) {
			Log.d("RtspThread", "Starting audio decoder with mime type \"$audioMimeType\"")
			if (isCodecSupported(audioMimeType)) {
				audioDecodeThread = AudioDecodeThread(
					audioMimeType,
					audioSampleRateHz,
					audioChannelCount,
					audioCodecConfig,
					audioFrameQueue
				)
				audioDecodeThread?.start()
			} else {
				Log.d("RtspThread", "Codec $audioMimeType is not supported on this device")
			}
		}
	}

	fun onRtspClientConnected() {
		Log.d("RtspThread", "onRtspClientConnected, playVideo")
		actionRtspStateChanged.invoke(STATE_READY.state, null)
		playVideo()
		playAudio()
	}

	fun showError(needShow: Boolean = false) {
		isTimeout = needShow
		activity.runOnUiThread {
			actionShowEmptyScreen.invoke(needShow)
		}
	}

	override fun run() {
		Log.d("RtspThread", "onRtspClientStarted()")
		Handler(Looper.getMainLooper()).postDelayed({
			if (isTimeout && isAlive) showError(needShow = true)
		}, 5000)

		val uri: Uri = Uri.parse(videoUrl)
		val port = if (uri.port == -1) DEFAULT_RTSP_PORT else uri.port
		var socket: Socket? = null
		try {
			Log.d(
				"RtspThread",
				"Connecting to ${uri.host.toString()}:$port scheme = ${uri.scheme}"
			)
			socket = if (uri.scheme?.lowercase() == "rtsps") {
				NetUtils.createSslSocketAndConnect(uri.host.toString(), port, 10000)
			} else {
				NetUtils.createSocketAndConnect(
					uri.host.toString(),
					port,
					10000
				) // java.net.SocketException: Broken pipe - unable to handle, unknown issue
			}
			// Blocking call until stopped variable is true or connection failed

			var array = arrayOfNulls<String>(0)
			array = videoUrl.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
			// rtsp://<username>:<password>@<host address>
			val isBasicAuth = array[2]?.contains("@") == true

			val uriRtsp = if (isBasicAuth) {
				val arrayUrl = videoUrl.split("@")
				"${uri.scheme}://${arrayUrl[1]}"
			} else {
				uri.toString()
			}
			val rtspClient =
				RtspClient.Builder(socket, uriRtsp, rtspStopped, proxyClientListener)
					.requestVideo(true)
					.requestAudio(true)
					.withDebug(true)

			if (isBasicAuth) {
				val name = array[1]?.substring(2)
				val pwd = array[2]?.split("@")?.get(0)
				Log.d("RtspThread", "name = $name pwd = $pwd")
				rtspClient.withCredentials(name, pwd)
			}
			rtspClient.build().execute() // java.net.SocketException: Broken pipe
			NetUtils.closeSocket(socket)
		} catch (e: IOException) {
			showError(needShow = true)
			counterError++
			Log.d("RtspThread", "RTSP Throwable counterError = $counterError")
			if (counterError > MAX_COUNTER) {
				NetUtils.closeSocket(socket)
				actionRestartRtsp.invoke()
			}
			e.printStackTrace()
		} catch (e: SocketTimeoutException) {
			showError(needShow = true)
			counterError++
			Log.d("RtspThread", "RTSP Throwable counterError = $counterError")
			if (counterError > MAX_COUNTER) {
				NetUtils.closeSocket(socket)
				actionRestartRtsp.invoke()
			}
			e.printStackTrace()
		} catch (e: SocketException) {
			showError(needShow = true)
			counterError++
			Log.d("RtspThread", "RTSP Throwable counterError = $counterError")
			if (counterError > MAX_COUNTER) {
				NetUtils.closeSocket(socket)
				actionRestartRtsp.invoke()
			}
			e.printStackTrace()
		} catch (e: Throwable) {
			showError(needShow = true)
			counterError++
			Log.d("RtspThread", "RTSP Throwable counterError = $counterError")
			if (counterError > MAX_COUNTER) {
				NetUtils.closeSocket(socket)
				actionRestartRtsp.invoke()
			}
			e.printStackTrace()
		}
	}

	private fun isCodecSupported(mimeType: String): Boolean {
		val mediaCodecList = MediaCodecList(MediaCodecList.ALL_CODECS)
		return mediaCodecList.codecInfos.any { codecInfo ->
			Log.d("RtspThread", "codecInfo = $codecInfo")
			codecInfo.supportedTypes.contains(mimeType)
		}
	}

	override fun interrupt() {
		Log.d("RtspThread", "interrupt()")
		super.interrupt()
	}

	override fun finishPopDueEmptyFrames() {
		Log.d("RtspThread", "finishPopDueEmptyFrames()")
		actionRtspStateChanged.invoke(STATE_ERROR_QUEUE_FRAME.state, null)
	}

	override fun finishDecoderDueEmptyFrames() {
		Log.d("RtspThread", "finishDecoderDueEmptyFrames()")
		actionRtspStateChanged.invoke(STATE_ERROR_VIDEO_DECODER.state, null)
	}

}

/** Statuses in RtspClient:

//OPTIONS rtsp://yakubouski.navek.dev:9554/stream3/live RTSP/1.0
//CSeq: 1
//User-Agent: Lavf58.67.100
//

//OPTIONS rtsp://yakubouski.navek.dev:9554/stream3/live RTSP/1.0
//CSeq: 2
//User-Agent: Lavf58.67.100
//Session: 18338180692471
//Authorization: Basic YWRtaW4xOmFkbWlu
//
//
//DESCRIBE rtsp://yakubouski.navek.dev:9554/stream3/live RTSP/1.0
//Accept: application/sdp
//CSeq: 3
//User-Agent: Lavf58.67.100
//Session: 18338180692471
//Authorization: Basic YWRtaW4xOmFkbWlu
//
//
//SETUP rtsp://yakubouski.navek.dev:9554/stream3/live/track=video RTSP/1.0
//Transport: RTP/AVP/TCP;unicast;interleaved=0-1
//CSeq: 4
//User-Agent: Lavf58.67.100
//Session: 18338180692471
//Authorization: Basic YWRtaW4xOmFkbWlu
//
//
//SETUP rtsp://yakubouski.navek.dev:9554/stream3/live/track=audio RTSP/1.0
//Transport: RTP/AVP/TCP;unicast;interleaved=2-3
//CSeq: 5
//User-Agent: Lavf58.67.100
//Session: 18338180692471
//Authorization: Basic YWRtaW4xOmFkbWlu
//
//
//PLAY rtsp://yakubouski.navek.dev:9554/stream3/live RTSP/1.0
//Range: npt=0.000-
//CSeq: 6
//User-Agent: Lavf58.67.100
//Session: 18338180692471
//Authorization: Basic YWRtaW4xOmFkbWlu
//
//
//TEARDOWN rtsp://yakubouski.navek.dev:9554/stream3/live RTSP/1.0
//CSeq: 7
//User-Agent: Lavf58.67.100
//Session: 18338180692471
//Authorization: Basic YWRtaW4xOmFkbWlu
 */