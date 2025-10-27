package com.medical.cmtcast.viewer

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL renderer for displaying STL 3D models
 */
class STLRenderer(private val context: Context, private val stlFilePath: String) : GLSurfaceView.Renderer {

    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    
    private var meshProgram: Int = 0
    private var vertexBuffer: FloatBuffer? = null
    private var normalBuffer: FloatBuffer? = null
    private var vertexCount: Int = 0
    
    var rotationX = 0f
    var rotationY = 0f
    var zoom = 3f
    
    companion object {
        private const val TAG = "STLRenderer"
        
        private const val VERTEX_SHADER_CODE = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uModelMatrix;
            attribute vec4 vPosition;
            attribute vec3 vNormal;
            varying vec3 vLighting;
            
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                
                // Simple lighting
                vec3 ambientLight = vec3(0.3, 0.3, 0.3);
                vec3 directionalLightColor = vec3(1.0, 1.0, 1.0);
                vec3 directionalVector = normalize(vec3(0.5, 0.5, 1.0));
                
                vec4 transformedNormal = uModelMatrix * vec4(vNormal, 0.0);
                float directional = max(dot(transformedNormal.xyz, directionalVector), 0.0);
                vLighting = ambientLight + (directionalLightColor * directional);
            }
        """
        
        private const val FRAGMENT_SHADER_CODE = """
            precision mediump float;
            varying vec3 vLighting;
            
            void main() {
                vec3 color = vec3(0.3, 0.6, 0.9); // Blue-ish color
                gl_FragColor = vec4(color * vLighting, 1.0);
            }
        """
    }
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.95f, 0.95f, 0.95f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        
        // Load and compile shaders
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
        
        meshProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
        
        // Load STL file
        loadSTL()
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        
        val ratio = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 1f, 10f)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        // Set camera position
        Matrix.setLookAtM(viewMatrix, 0, 
            0f, 0f, zoom,  // Eye position
            0f, 0f, 0f,    // Look at center
            0f, 1f, 0f)    // Up vector
        
        // Apply rotations
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)
        
        // Calculate MVP matrix
        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
        
        // Draw mesh
        drawMesh()
    }
    
    private fun drawMesh() {
        if (vertexBuffer == null || vertexCount == 0) return
        
        GLES20.glUseProgram(meshProgram)
        
        // Get shader attribute locations
        val positionHandle = GLES20.glGetAttribLocation(meshProgram, "vPosition")
        val normalHandle = GLES20.glGetAttribLocation(meshProgram, "vNormal")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(meshProgram, "uMVPMatrix")
        val modelMatrixHandle = GLES20.glGetUniformLocation(meshProgram, "uModelMatrix")
        
        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(normalHandle)
        
        // Prepare vertex data
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer)
        
        // Apply matrices
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, vPMatrix, 0)
        GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0)
        
        // Draw triangles
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        
        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }
    
    private fun loadSTL() {
        try {
            val file = File(stlFilePath)
            if (!file.exists()) {
                Log.e(TAG, "STL file not found: $stlFilePath")
                return
            }
            
            val lines = file.readLines()
            val vertices = mutableListOf<Float>()
            val normals = mutableListOf<Float>()
            
            var currentNormal = floatArrayOf(0f, 0f, 1f)
            
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("facet normal") -> {
                        val parts = trimmed.split(" ")
                        if (parts.size >= 5) {
                            currentNormal = floatArrayOf(
                                parts[2].toFloatOrNull() ?: 0f,
                                parts[3].toFloatOrNull() ?: 0f,
                                parts[4].toFloatOrNull() ?: 0f
                            )
                        }
                    }
                    trimmed.startsWith("vertex") -> {
                        val parts = trimmed.split(" ")
                        if (parts.size >= 4) {
                            // Add vertex
                            vertices.add(parts[1].toFloatOrNull() ?: 0f)
                            vertices.add(parts[2].toFloatOrNull() ?: 0f)
                            vertices.add(parts[3].toFloatOrNull() ?: 0f)
                            
                            // Add normal for this vertex
                            normals.add(currentNormal[0])
                            normals.add(currentNormal[1])
                            normals.add(currentNormal[2])
                        }
                    }
                }
            }
            
            if (vertices.isEmpty()) {
                Log.e(TAG, "No vertices loaded from STL")
                return
            }
            
            // Center and scale the model
            val scaledVertices = centerAndScaleModel(vertices)
            
            // Create vertex buffer
            val vbb = ByteBuffer.allocateDirect(scaledVertices.size * 4)
            vbb.order(ByteOrder.nativeOrder())
            vertexBuffer = vbb.asFloatBuffer()
            vertexBuffer?.put(scaledVertices.toFloatArray())
            vertexBuffer?.position(0)
            
            // Create normal buffer
            val nbb = ByteBuffer.allocateDirect(normals.size * 4)
            nbb.order(ByteOrder.nativeOrder())
            normalBuffer = nbb.asFloatBuffer()
            normalBuffer?.put(normals.toFloatArray())
            normalBuffer?.position(0)
            
            vertexCount = vertices.size / 3
            
            Log.d(TAG, "Loaded STL with ${vertexCount} vertices")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading STL", e)
        }
    }
    
    private fun centerAndScaleModel(vertices: List<Float>): List<Float> {
        if (vertices.isEmpty()) return vertices
        
        // Find bounding box
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = Float.MIN_VALUE
        
        for (i in vertices.indices step 3) {
            minX = minOf(minX, vertices[i])
            maxX = maxOf(maxX, vertices[i])
            minY = minOf(minY, vertices[i + 1])
            maxY = maxOf(maxY, vertices[i + 1])
            minZ = minOf(minZ, vertices[i + 2])
            maxZ = maxOf(maxZ, vertices[i + 2])
        }
        
        // Calculate center and scale
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val centerZ = (minZ + maxZ) / 2f
        
        val sizeX = maxX - minX
        val sizeY = maxY - minY
        val sizeZ = maxZ - minZ
        val maxSize = maxOf(sizeX, sizeY, sizeZ)
        val scale = if (maxSize > 0) 2f / maxSize else 1f
        
        // Center and scale
        return vertices.mapIndexed { i, v ->
            when (i % 3) {
                0 -> (v - centerX) * scale
                1 -> (v - centerY) * scale
                else -> (v - centerZ) * scale
            }
        }
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
