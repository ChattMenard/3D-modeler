package com.medical.cmtcast.viewer

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.medical.cmtcast.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class Model3DViewerActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: STLRenderer
    private lateinit var tvInstructions: TextView
    private lateinit var loadingOverlay: LinearLayout
    
    private var previousX = 0f
    private var previousY = 0f
    private var previousDistance = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model3d_viewer)

        tvInstructions = findViewById(R.id.tvInstructions)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        
        // Get STL file path from intent
        val stlPath = intent.getStringExtra("stl_path")
        
        if (stlPath == null) {
            Toast.makeText(this, "Error: No STL file specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Setup OpenGL surface view
        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.setEGLContextClientVersion(2)
        
        renderer = STLRenderer(this, stlPath)
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        
        // Setup touch controls
        glSurfaceView.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
        
        // Hide loading overlay after a short delay to allow rendering to start
        lifecycleScope.launch {
            delay(1500) // Give OpenGL time to load and render first frame
            withContext(Dispatchers.Main) {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this@Model3DViewerActivity, "3D Model Loaded", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    // Single finger - rotate
                    val deltaX = event.x - previousX
                    val deltaY = event.y - previousY
                    
                    renderer.rotationY += deltaX * 0.5f
                    renderer.rotationX += deltaY * 0.5f
                    
                    previousX = event.x
                    previousY = event.y
                    
                } else if (event.pointerCount == 2) {
                    // Two fingers - zoom
                    val x = event.getX(0) - event.getX(1)
                    val y = event.getY(0) - event.getY(1)
                    val distance = kotlin.math.sqrt((x * x + y * y).toDouble()).toFloat()
                    
                    if (previousDistance > 0) {
                        val scale = previousDistance / distance
                        renderer.zoom *= scale
                        renderer.zoom = renderer.zoom.coerceIn(1f, 10f)
                    }
                    
                    previousDistance = distance
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                previousDistance = 0f
            }
        }
        return true
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }
}
