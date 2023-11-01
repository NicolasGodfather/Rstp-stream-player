package com.example.rtsp

import android.annotation.SuppressLint
import android.util.Log
import java.util.concurrent.*

@SuppressLint("LogNotTimber")
class FrameQueue(val callback: FrameQueueCallback) {

	private var counterEmptyFrames = 0
	val queue: BlockingQueue<Frame> = ArrayBlockingQueue(60)

	class Frame(val data: ByteArray, val offset: Int, val length: Int, val timestamp: Long)

	companion object {
		private val TAG: String = FrameQueue::class.java.simpleName
		/**
		 * Counter equals 10 frames per 10 second.
		 * It reached to 10 times if we don't have connection 10 second.
		 */
		const val MAX_COUNTER_EMPTY_FRAMES_QUEUE = 10
	}

	interface FrameQueueCallback {
		/**
		 * Finish pop queue, to make request for show live or archive
		 */
		fun finishPopDueEmptyFrames()
	}

	@Throws(InterruptedException::class)
	fun push(frame: Frame): Boolean {
		if (queue.offer(frame, 10, TimeUnit.MILLISECONDS)) {
			return true
		}
//		Log.d(TAG, "Cannot add frame, queue is full")
		return false
	}

	@Throws(InterruptedException::class)
	fun pop(): Frame? {
		try {
			val frame: Frame? = queue.poll(1000, TimeUnit.MILLISECONDS) // poll each 1 second
			if (frame == null) {
				counterEmptyFrames++
				Log.d(TAG, "Cannot get frame, queue is empty, weak internet connection or damaged stream")
				if (counterEmptyFrames == MAX_COUNTER_EMPTY_FRAMES_QUEUE) {
					callback.finishPopDueEmptyFrames()
					counterEmptyFrames = 0
				}
			} else {
				counterEmptyFrames = 0
			}
			return frame
		} catch (e: InterruptedException) {
			e.printStackTrace()
		}
		return null
	}

	fun clear() {
		queue.clear()
	}

}
