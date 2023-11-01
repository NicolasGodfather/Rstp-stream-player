package com.example.rtsp

import android.media.*
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class VideoDecodeThread(
	private var surface: Surface?,
	private val mimeType: String,
	private val width: Int,
	private val height: Int,
	private val videoFrameQueue: FrameQueue,
	private val videoDecoderCallback: VideoDecoderCallback,
	private val actionPlaying: (Long) -> Unit,  // with dif in timestamps between two frames
	private val actionChangeTimeline: () -> Unit
): Thread() {

	companion object {
		private val TAG: String = VideoDecodeThread::class.java.simpleName
		private val DEQUEUE_INPUT_TIMEOUT_US = TimeUnit.MILLISECONDS.toMicros(500)
		private val DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US = TimeUnit.MILLISECONDS.toMicros(100)
		/**
		 * Counter equals 10 frames.
		 */
		const val MAX_COUNTER_EMPTY_FRAMES_DECODER = 10
	}

	interface VideoDecoderCallback {
		/**
		 * Finish pop queue, to make request for show live or archive
		 */
		fun finishDecoderDueEmptyFrames()
	}

	private var exitFlag: AtomicBoolean = AtomicBoolean(false)
	private var previousTimestamp = 0L
	private var currentTimestamp = 0L
	private var isTimelineChanged = false
	private var counterEmptyFrames = 0

	@Volatile
	private var isPaused = false
	private val pauseLock = Object()

	fun pauseDecoding() {
		synchronized(pauseLock) {
			isPaused = true
		}
	}

	fun resumeDecoding() {
		synchronized(pauseLock) {
			isPaused = false
			pauseLock.notify()
		}
	}

	fun stopAsync() {
		exitFlag.set(true)
//		videoFrameQueue.clear()
		// Wake up sleep() code
		interrupt()
	}

	private fun getDecoderSafeWidthHeight(decoder: MediaCodec): Pair<Int, Int> {
		val capabilities = decoder.codecInfo.getCapabilitiesForType(mimeType).videoCapabilities
		return if (capabilities.isSizeSupported(width, height)) {
			Pair(width, height)
		} else {
			val widthAlignment = capabilities.widthAlignment
			val heightAlignment = capabilities.heightAlignment
			Pair(
				ceilDivide(width, widthAlignment) * widthAlignment,
				ceilDivide(height, heightAlignment) * heightAlignment
			)
		}
	}

	fun ceilDivide(numerator: Int, denominator: Int): Int {
		return (numerator + denominator - 1) / denominator
	}

	override fun run() {
		Log.d(TAG, "$name started")

		try {
			val decoder = MediaCodec.createDecoderByType(mimeType)
			val widthHeight = getDecoderSafeWidthHeight(decoder)
			val format =
				MediaFormat.createVideoFormat(mimeType, widthHeight.first, widthHeight.second)

			Log.d(
				TAG,
				"Configuring surface ${widthHeight.first}x${widthHeight.second} w/ '$mimeType', max instances: ${
					decoder.codecInfo.getCapabilitiesForType(mimeType).maxSupportedInstances
				}"
			)
			if (!exitFlag.get()) decoder.configure(
				format,
				surface,
				null,
				0
			) // java.lang.IllegalArgumentException when slow connection

			decoder.start()
			Log.d(TAG, "Started surface decoder")

			val bufferInfo = MediaCodec.BufferInfo()

			try {
				// Main loop
				while (!exitFlag.get()) {
					synchronized(pauseLock) {
						while (isPaused) {
							try {
								pauseLock.wait()
							} catch (e: InterruptedException) {
								Log.d(TAG, e.message ?: "${e.printStackTrace()}")
							}
						}
					}
					val inIndex: Int = decoder.dequeueInputBuffer(DEQUEUE_INPUT_TIMEOUT_US)
					if (inIndex >= 0) {
						// fill inputBuffers[inputBufferIndex] with valid data
						val byteBuffer: ByteBuffer? = decoder.getInputBuffer(inIndex)
						byteBuffer?.rewind()

						// Preventing BufferOverflowException
						// if (length > byteBuffer.limit()) throw DecoderFatalException("Error")

						if (exitFlag.get() || isInterrupted) break // handled interrupted exception
						val frame = videoFrameQueue.pop()
						if (frame == null) {
							Log.d(TAG, "Empty video frame from decoder")
							// Release input buffer
							decoder.queueInputBuffer(inIndex, 0, 0, 0L, 0)
							counterEmptyFrames++
							Log.d(
								TAG,
								"Cannot get frame, queue is empty counterEmptyFrames = $counterEmptyFrames"
							)
							if (counterEmptyFrames == MAX_COUNTER_EMPTY_FRAMES_DECODER) {
								videoDecoderCallback.finishDecoderDueEmptyFrames()
								counterEmptyFrames = 0
							}
						} else {
							counterEmptyFrames = 0
							byteBuffer?.put(frame.data, frame.offset, frame.length)
							decoder.queueInputBuffer(
								inIndex,
								frame.offset,
								frame.length,
								frame.timestamp,
								0
							)
							currentTimestamp = frame.timestamp
							if (previousTimestamp == 0L) previousTimestamp = currentTimestamp
						}
					}

					if (exitFlag.get() || isInterrupted) break // handled interrupted exception
					when (val outIndex = decoder.dequeueOutputBuffer(
						bufferInfo,
						DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US
					)) {
						MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Log.d(
							TAG,
							"Decoder format changed: ${decoder.outputFormat}"
						)

						MediaCodec.INFO_TRY_AGAIN_LATER -> Log.d(
							TAG, "No output from decoder available"
						)

						else -> {
							if (outIndex >= 0) {
								decoder.releaseOutputBuffer(
									outIndex,
									bufferInfo.size != 0 && !exitFlag.get()
								)
								actionPlaying.invoke((currentTimestamp - previousTimestamp) / 1000)
								if (!isTimelineChanged) {
									actionChangeTimeline.invoke()   // only once
									isTimelineChanged = true
								}
								previousTimestamp = currentTimestamp
							}
						}
					}

					// All decoded frames have been rendered, we can stop playing now
					if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
						Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM")
						break
					}
				}

				// Drain decoder
				val inIndex: Int = decoder.dequeueInputBuffer(DEQUEUE_INPUT_TIMEOUT_US)
				if (inIndex >= 0) {
					decoder.queueInputBuffer(
						inIndex,
						0,
						0,
						0L,
						MediaCodec.BUFFER_FLAG_END_OF_STREAM
					)
				} else {
					Log.w(TAG, "Not able to signal end of stream")
				}

				decoder.stop()
				decoder.release()
				videoFrameQueue.clear()
			} catch (e: Exception) {
				Log.d(TAG, e.message ?: "${e.printStackTrace()}")
			}

		} catch (e: Exception) {
			Log.e(TAG, "$name stopped due to '${e.message}'")
			// While configuring stopAsync can be called and surface released. Just exit.
			if (!exitFlag.get()) e.printStackTrace()
			return
		}

		Log.d(TAG, "$name stopped")
	}

}

