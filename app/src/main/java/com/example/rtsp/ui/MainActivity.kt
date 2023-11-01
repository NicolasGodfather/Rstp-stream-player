package com.example.rtsp.ui

import android.graphics.SurfaceTexture
import android.os.Bundle
import android.text.*
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.widget.TextView.OnEditorActionListener
import androidx.appcompat.app.AppCompatActivity
import com.example.rtsp.*

class MainActivity: AppCompatActivity(), TextureView.SurfaceTextureListener {

	private var surface: Surface? = null
	var rtspThread: RtspThread? = null
	var url = ""

	lateinit var edtUrl: EditText
	lateinit var tvButton: TextView
	lateinit var surfaceViewVideo: TextureView

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_main)

		surfaceViewVideo = findViewById(R.id.surfaceViewVideo)
		edtUrl = findViewById(R.id.edtUrl)
		tvButton = findViewById(R.id.tvButton)
		surfaceViewVideo.surfaceTextureListener = this

		edtUrl.addTextChangedListener(object: TextWatcher {
			override fun afterTextChanged(s: Editable) {}
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
				url = s.toString()
				Log.d("afterTextChanged", "url = $url")
			}
		})

		edtUrl.setOnEditorActionListener(OnEditorActionListener { v, actionId, event ->
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				startStream()
				return@OnEditorActionListener true
			}
			false
		})
		edtUrl.setText(url)

		tvButton.setOnClickListener {
			if (rtspThread == null) {
				startStream()
				tvButton.text = "STOP RTSP STREAM"
			} else {
				stopStream()
				tvButton.text = "START RTSP STREAM"
			}
		}
	}

	private fun startStream() {
		if (url.isEmpty()) return

		if (surface != null && rtspThread == null && url.isNotEmpty()) {
			Log.d("LOGS", "onSurfaceTextureAvailable showVideo url = $url")
			rtspThread = RtspThread(this, {
				// restart
				stopStream()
				startStream()
			}, { it1, it2 ->
				// handle states, errors
			}, {
				// show empty screen
			})
			rtspThread?.surface = surface
			rtspThread?.videoUrl = url
			rtspThread?.start()
		} else {
			Log.d("LOGS", "onSurfaceTextureAvailable showVideo isSurfaceInstalled = false")
		}
	}

	private fun stopStream() {
		rtspThread?.onRtspClientReleased()
		rtspThread = null
		surface?.release()
		surface = null
	}

	override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
		if (!isDestroyed && !isFinishing) {
			surface = Surface(texture)
			startStream()
		}
	}

	override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
	}

	override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
		return false
	}

	override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
		if (surface == null) {
			surface = Surface(texture)
			Log.d("LOGS", "onSurfaceTextureAvailable onSurfaceTextureUpdated: showVideo()")
			startStream()
		}
	}

	override fun onDestroy() {
		surface?.release()
		surface = null
		super.onDestroy()
	}

}