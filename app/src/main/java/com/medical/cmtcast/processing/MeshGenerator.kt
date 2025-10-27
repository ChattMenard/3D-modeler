package com.medical.cmtcast.processing

import android.content.Context
import android.util.Log
import com.medical.cmtcast.settings.AppSettings
import java.io.File
import java.io.DataOutputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import kotlin.math.*

/**
 * Generates 3D mesh from point cloud and exports to STL
 */
class MeshGenerator(private val context: Context) {
    
    companion object {
        private const val TAG = "MeshGenerator"
    }
    
    data class Triangle(
        val v1: Reconstruction3D.Point3D,
        val v2: Reconstruction3D.Point3D,
        val v3: Reconstruction3D.Point3D
    ) {
        fun normal(): Reconstruction3D.Point3D {
            // Calculate face normal using cross product
            val u = Reconstruction3D.Point3D(
                v2.x - v1.x,
                v2.y - v1.y,
                v2.z - v1.z
            )
            val v = Reconstruction3D.Point3D(
                v3.x - v1.x,
                v3.y - v1.y,
                v3.z - v1.z
            )
            
            val nx = u.y * v.z - u.z * v.y
            val ny = u.z * v.x - u.x * v.z
            val nz = u.x * v.y - u.y * v.x
            
            // Normalize
            val length = sqrt(nx * nx + ny * ny + nz * nz)
            return if (length > 0) {
                Reconstruction3D.Point3D(nx / length, ny / length, nz / length)
            } else {
                Reconstruction3D.Point3D(0.0, 1.0, 0.0)
            }
        }
    }
    
    data class Mesh(
        val triangles: List<Triangle>,
        val bounds: Pair<Reconstruction3D.Point3D, Reconstruction3D.Point3D>
    )
    
    /**
     * Create mesh from point cloud using surface reconstruction
     */
    fun createMesh(points: List<Reconstruction3D.Point3D>): Mesh {
        Log.d(TAG, "Creating mesh from ${points.size} points")
        
        // Validate input
        if (points.isEmpty()) {
            Log.e(TAG, "Cannot create mesh: No points provided")
            throw IllegalArgumentException("Cannot create mesh from empty point cloud")
        }
        
        if (points.size < 4) {
            Log.e(TAG, "Cannot create mesh: Insufficient points (${points.size}), need at least 4")
            throw IllegalArgumentException("Cannot create mesh: Need at least 4 points, got ${points.size}")
        }
        
        // Filter out invalid points (NaN, infinity)
        val validPoints = points.filter { point ->
            point.x.isFinite() && point.y.isFinite() && point.z.isFinite()
        }
        
        if (validPoints.size < points.size) {
            Log.w(TAG, "Filtered out ${points.size - validPoints.size} invalid points")
        }
        
        if (validPoints.size < 4) {
            Log.e(TAG, "Cannot create mesh: Too few valid points (${validPoints.size}) after filtering")
            throw IllegalArgumentException("Cannot create mesh: Too few valid points after filtering invalid coordinates")
        }
        
        val triangles = mutableListOf<Triangle>()
        
        // Sort points by height (Y axis) to create layers
        val sortedPoints = validPoints.sortedBy { it.y }
        
        // Calculate adaptive slice height based on point cloud density
        val minY = sortedPoints.first().y
        val maxY = sortedPoints.last().y
        val heightRange = maxY - minY
        
        if (heightRange <= 0) {
            Log.e(TAG, "Cannot create mesh: All points have same Y coordinate")
            throw IllegalArgumentException("Cannot create mesh: Point cloud has no height variation")
        }
        
        // Adaptive slice height: aim for 8-20 slices depending on height
        val targetSlices = 12
        val sliceHeight = maxOf(heightRange / targetSlices, 2.0) // minimum 2mm slices
        val numSlices = (heightRange / sliceHeight).toInt().coerceAtLeast(2)
        
        Log.d(TAG, "Creating $numSlices horizontal slices (height range: ${heightRange}mm, slice height: ${sliceHeight}mm)")
        
        var totalTriangles = 0
        var emptySlices = 0
        
        for (i in 0 until numSlices - 1) {
            val y1 = minY + i * sliceHeight
            val y2 = minY + (i + 1) * sliceHeight
            
            // Get points in each slice with some overlap to ensure continuity
            val slice1 = sortedPoints.filter { it.y >= y1 - sliceHeight * 0.1 && it.y <= y1 + sliceHeight * 1.1 }
            val slice2 = sortedPoints.filter { it.y >= y2 - sliceHeight * 0.1 && it.y <= y2 + sliceHeight * 1.1 }
            
            Log.d(TAG, "Slice $i: ${slice1.size} points, Slice ${i+1}: ${slice2.size} points")
            
            if (slice1.size >= 3 && slice2.size >= 3) {
                try {
                    // Sort points in each slice by angle around center
                    val center1 = calculateCenter(slice1)
                    val center2 = calculateCenter(slice2)
                    
                    val sorted1 = sortByAngle(slice1, center1)
                    val sorted2 = sortByAngle(slice2, center2)
                    
                    // Connect the two slices with triangles
                    val sliceTriangles = connectSlices(sorted1, sorted2)
                    triangles.addAll(sliceTriangles)
                    totalTriangles += sliceTriangles.size
                    
                    Log.d(TAG, "Generated ${sliceTriangles.size} triangles for slice $i")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to connect slice $i: ${e.message}")
                }
            } else {
                emptySlices++
                Log.w(TAG, "Skipping slice $i: insufficient points (${slice1.size}, ${slice2.size})")
            }
        }
        
        if (emptySlices > numSlices / 2) {
            Log.w(TAG, "Warning: $emptySlices/$numSlices slices were empty or had insufficient points")
        }
        
        val bounds = calculateBounds(validPoints)
        
        Log.d(TAG, "Generated ${triangles.size} triangles total")
        
        if (triangles.isEmpty()) {
            Log.e(TAG, "Mesh generation failed: No triangles created")
            throw RuntimeException("Mesh generation failed: Unable to create any triangles from point cloud")
        }
        
        return Mesh(triangles, bounds)
    }
    
    /**
     * Connect two horizontal slices with triangles
     */
    private fun connectSlices(
        slice1: List<Reconstruction3D.Point3D>,
        slice2: List<Reconstruction3D.Point3D>
    ): List<Triangle> {
        val triangles = mutableListOf<Triangle>()
        val n1 = slice1.size
        val n2 = slice2.size
        
        if (n1 < 3 || n2 < 3) {
            Log.w(TAG, "Cannot connect slices: insufficient points ($n1, $n2)")
            return triangles
        }
        
        // Create triangle strip between slices
        var i1 = 0
        var i2 = 0
        var iterations = 0
        val maxIterations = (n1 + n2) * 2 // Prevent infinite loops
        
        while (i1 < n1 && i2 < n2 && iterations < maxIterations) {
            try {
                val p1 = slice1[i1]
                val p2 = slice1[(i1 + 1) % n1]
                val p3 = slice2[i2]
                val p4 = slice2[(i2 + 1) % n2]
                
                // Validate points are not identical (would create degenerate triangles)
                if (arePointsDistinct(p1, p3, p4) && arePointsDistinct(p1, p4, p2)) {
                    // Calculate distances to decide which diagonal to use
                    val d1 = distance(p1, p4)
                    val d2 = distance(p2, p3)
                    
                    if (d1 < d2) {
                        triangles.add(Triangle(p1, p3, p4))
                        triangles.add(Triangle(p1, p4, p2))
                    } else {
                        triangles.add(Triangle(p1, p3, p2))
                        triangles.add(Triangle(p2, p3, p4))
                    }
                }
                
                // Advance both indices to progress around both slices
                if ((i1 + 1) / n1.toDouble() < (i2 + 1) / n2.toDouble()) {
                    i1++
                } else {
                    i2++
                }
                
                iterations++
            } catch (e: Exception) {
                Log.w(TAG, "Error creating triangle at indices ($i1, $i2): ${e.message}")
                i1++
                i2++
                iterations++
            }
        }
        
        if (iterations >= maxIterations) {
            Log.w(TAG, "Slice connection terminated due to iteration limit")
        }
        
        return triangles
    }
    
    /**
     * Check if three points are distinct enough to form a valid triangle
     */
    private fun arePointsDistinct(p1: Reconstruction3D.Point3D, p2: Reconstruction3D.Point3D, p3: Reconstruction3D.Point3D): Boolean {
        val minDistance = 0.1 // Minimum distance in mm
        return distance(p1, p2) > minDistance && 
               distance(p2, p3) > minDistance && 
               distance(p1, p3) > minDistance
    }
    
    /**
     * Calculate center point of a slice
     */
    private fun calculateCenter(points: List<Reconstruction3D.Point3D>): Reconstruction3D.Point3D {
        return Reconstruction3D.Point3D(
            points.map { it.x }.average(),
            points.map { it.y }.average(),
            points.map { it.z }.average()
        )
    }
    
    /**
     * Sort points by angle around center (for circular ordering)
     */
    private fun sortByAngle(
        points: List<Reconstruction3D.Point3D>,
        center: Reconstruction3D.Point3D
    ): List<Reconstruction3D.Point3D> {
        return points.sortedBy { point ->
            atan2(point.z - center.z, point.x - center.x)
        }
    }
    
    /**
     * Calculate distance between two points
     */
    private fun distance(p1: Reconstruction3D.Point3D, p2: Reconstruction3D.Point3D): Double {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val dz = p2.z - p1.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
    
    /**
     * Apply Laplacian smoothing to mesh for better surface quality
     * Smooths the mesh by averaging each vertex with its neighbors
     */
    fun smoothMesh(mesh: Mesh, iterations: Int = 3): Mesh {
        Log.d(TAG, "Applying Laplacian smoothing ($iterations iterations)")
        
        var currentMesh = mesh
        
        repeat(iterations) { iteration ->
            // Build vertex adjacency map (which vertices are connected)
            val vertexNeighbors = mutableMapOf<Reconstruction3D.Point3D, MutableList<Reconstruction3D.Point3D>>()
            
            currentMesh.triangles.forEach { triangle ->
                // Each vertex in a triangle is a neighbor to the other two
                addNeighbor(vertexNeighbors, triangle.v1, triangle.v2)
                addNeighbor(vertexNeighbors, triangle.v1, triangle.v3)
                addNeighbor(vertexNeighbors, triangle.v2, triangle.v1)
                addNeighbor(vertexNeighbors, triangle.v2, triangle.v3)
                addNeighbor(vertexNeighbors, triangle.v3, triangle.v1)
                addNeighbor(vertexNeighbors, triangle.v3, triangle.v2)
            }
            
            // Create a map of original vertex -> smoothed vertex
            val smoothedVertices = mutableMapOf<Reconstruction3D.Point3D, Reconstruction3D.Point3D>()
            
            vertexNeighbors.forEach { (vertex, neighbors) ->
                if (neighbors.isNotEmpty()) {
                    // Calculate average position of neighbors
                    val avgX = neighbors.map { it.x }.average()
                    val avgY = neighbors.map { it.y }.average()
                    val avgZ = neighbors.map { it.z }.average()
                    
                    // Blend between original and average (0.5 = 50% smoothing)
                    val smoothFactor = 0.5
                    smoothedVertices[vertex] = Reconstruction3D.Point3D(
                        vertex.x * (1 - smoothFactor) + avgX * smoothFactor,
                        vertex.y * (1 - smoothFactor) + avgY * smoothFactor,
                        vertex.z * (1 - smoothFactor) + avgZ * smoothFactor
                    )
                } else {
                    smoothedVertices[vertex] = vertex
                }
            }
            
            // Apply smoothed positions to all triangles
            val smoothedTriangles = currentMesh.triangles.map { triangle ->
                Triangle(
                    smoothedVertices[triangle.v1] ?: triangle.v1,
                    smoothedVertices[triangle.v2] ?: triangle.v2,
                    smoothedVertices[triangle.v3] ?: triangle.v3
                )
            }
            
            currentMesh = Mesh(smoothedTriangles, currentMesh.bounds)
            Log.d(TAG, "Completed smoothing iteration ${iteration + 1}/$iterations")
        }
        
        return currentMesh
    }
    
    /**
     * Helper to add neighbor relationship (avoiding duplicates)
     */
    private fun addNeighbor(
        map: MutableMap<Reconstruction3D.Point3D, MutableList<Reconstruction3D.Point3D>>,
        vertex: Reconstruction3D.Point3D,
        neighbor: Reconstruction3D.Point3D
    ) {
        val neighbors = map.getOrPut(vertex) { mutableListOf() }
        if (!neighbors.contains(neighbor)) {
            neighbors.add(neighbor)
        }
    }
    
    /**
     * Apply offset to mesh for cast thickness
     */
    fun applyThickness(mesh: Mesh): Mesh {
        val thickness = AppSettings.getCastThickness(context).toDouble()  // Get from settings
        Log.d(TAG, "Applying thickness: ${thickness}mm to ${mesh.triangles.size} triangles")
        
        // Memory check before processing
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val memoryPercent = (usedMemory.toDouble() / maxMemory.toDouble() * 100).toInt()
        
        Log.d(TAG, "Memory before applyThickness: ${usedMemory}MB / ${maxMemory}MB ($memoryPercent%)")
        
        if (memoryPercent > 85) {
            Log.w(TAG, "WARNING: High memory usage ($memoryPercent%) - may cause crash")
            // Force garbage collection to free up memory
            System.gc()
            Thread.sleep(100)
        }
        
        try {
            val thickenedTriangles = mesh.triangles.mapIndexed { index, triangle ->
                // Progress logging for large meshes
                if (index % 1000 == 0 && index > 0) {
                    Log.d(TAG, "Thickening progress: $index/${mesh.triangles.size}")
                }
                
                val normal = triangle.normal()
                
                // Validate normal is not NaN or Infinity
                if (!normal.x.isFinite() || !normal.y.isFinite() || !normal.z.isFinite()) {
                    Log.w(TAG, "Invalid normal detected at triangle $index, using default")
                    return@mapIndexed triangle // Keep original triangle if normal is invalid
                }
                
                // Offset each vertex along normal
                val v1 = Reconstruction3D.Point3D(
                    triangle.v1.x + normal.x * thickness,
                    triangle.v1.y + normal.y * thickness,
                    triangle.v1.z + normal.z * thickness
                )
                val v2 = Reconstruction3D.Point3D(
                    triangle.v2.x + normal.x * thickness,
                    triangle.v2.y + normal.y * thickness,
                    triangle.v2.z + normal.z * thickness
                )
                val v3 = Reconstruction3D.Point3D(
                    triangle.v3.x + normal.x * thickness,
                    triangle.v3.y + normal.y * thickness,
                    triangle.v3.z + normal.z * thickness
                )
                
                Triangle(v1, v2, v3)
            }
            
            Log.d(TAG, "Thickness applied successfully to ${thickenedTriangles.size} triangles")
            return mesh.copy(triangles = thickenedTriangles)
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OUT OF MEMORY during applyThickness - returning original mesh", e)
            // Return original mesh if we run out of memory
            throw RuntimeException("Out of memory while applying thickness. Try with fewer points or reduce quality.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying thickness: ${e.message}", e)
            throw RuntimeException("Failed to apply cast thickness: ${e.message}", e)
        }
    }
    
    /**
     * Export mesh to binary STL format - writes directly to file (MEMORY EFFICIENT!)
     * Binary STL uses ~70% less memory than ASCII and is faster
     */
    fun exportToBinarySTL(mesh: Mesh, outputFile: File): File {
        Log.d(TAG, "Exporting ${mesh.triangles.size} triangles to binary STL format")
        
        try {
            java.io.DataOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(outputFile))).use { out ->
                // Write 80-byte header
                val header = ByteArray(80)
                "Binary STL - CMT Cast".toByteArray().copyInto(header)
                out.write(header)
                
                // Write triangle count (little-endian)
                out.writeInt(Integer.reverseBytes(mesh.triangles.size))
                
                var progressCounter = 0
                for ((index, triangle) in mesh.triangles.withIndex()) {
                    // Progress logging for large exports
                    if (index % 5000 == 0 && index > 0) {
                        progressCounter++
                        Log.d(TAG, "Binary STL export: $index/${mesh.triangles.size} (${index * 100 / mesh.triangles.size}%)")
                    }
                    
                    val normal = triangle.normal()
                    
                    // Validate values
                    if (!normal.x.isFinite() || !normal.y.isFinite() || !normal.z.isFinite()) {
                        Log.w(TAG, "Skipping triangle $index with invalid normal")
                        continue
                    }
                    
                    // Write normal (3 floats, little-endian)
                    out.writeFloat(java.lang.Float.intBitsToFloat(Integer.reverseBytes(java.lang.Float.floatToIntBits(normal.x.toFloat()))))
                    out.writeFloat(java.lang.Float.intBitsToFloat(Integer.reverseBytes(java.lang.Float.floatToIntBits(normal.y.toFloat()))))
                    out.writeFloat(java.lang.Float.intBitsToFloat(Integer.reverseBytes(java.lang.Float.floatToIntBits(normal.z.toFloat()))))
                    
                    // Write vertex 1
                    out.writeFloat(java.lang.Float.intBitsToFloat(Integer.reverseBytes(java.lang.Float.floatToIntBits(triangle.v1.x.toFloat()))))
                    out.writeFloat(java.lang.Float.intBitsToFloat(Integer.reverseBytes(java.lang.Float.floatToIntBits(triangle.v1.y.toFloat()))))
                    out.writeFloat(java.lang.Float.intBitsToFloat(Integer.reverseBytes(java.lang.Float.floatToIntBits(triangle.v1.z.toFloat()))))
                    
                    // Write vertex 2
                    out.writeFloat(java.lang.Float.intBitsToFloat(Integer.reverseBytes(java.lang.Float.floatToIntBits(triangle.v2.x.toFloat()))))
                    out.writeFloat(java.lang.Float.intBitsToFloat(Integer.reverseBytes(java.lang.Float.floatToIntBits(triangle.v2.y.toFloat()))))
                    out.writeFloat(java.lang.Float.intBitsToFloat(Integer.reverseBytes(java.lang.Float.floatToIntBits(triangle.v2.z.toFloat()))))
                    
                    // Write vertex 3
                    out.writeFloat(java.lang.Float.intBitsToFloat(Integer.reverseBytes(java.lang.Float.floatToIntBits(triangle.v3.x.toFloat()))))
                    out.writeFloat(java.lang.Float.intBitsToFloat(Integer.reverseBytes(java.lang.Float.floatToIntBits(triangle.v3.y.toFloat()))))
                    out.writeFloat(java.lang.Float.intBitsToFloat(Integer.reverseBytes(java.lang.Float.floatToIntBits(triangle.v3.z.toFloat()))))
                    
                    // Write attribute byte count (always 0)
                    out.writeShort(0)
                }
                
                out.flush()
            }
            
            val fileSizeMB = outputFile.length() / (1024 * 1024)
            Log.d(TAG, "Binary STL export completed: ${outputFile.absolutePath} (${fileSizeMB}MB)")
            
            return outputFile
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OUT OF MEMORY during binary STL export", e)
            System.gc()
            throw RuntimeException("Out of memory while exporting STL file. The mesh is too large.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to binary STL: ${e.message}", e)
            throw RuntimeException("Failed to export binary STL: ${e.message}", e)
        }
    }
    
    /**
     * Export mesh to STL format (ASCII) - DEPRECATED: Use exportToBinarySTL() instead
     * Uses efficient StringBuilder with pre-allocated capacity to avoid memory issues
     */
    @Deprecated("Use exportToBinarySTL() instead - uses 70% less memory")
    fun exportToSTL(mesh: Mesh, name: String = "leg_cast"): String {
        Log.d(TAG, "Exporting ${mesh.triangles.size} triangles to STL format")
        
        // Memory check before export
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val memoryPercent = (usedMemory.toDouble() / maxMemory.toDouble() * 100).toInt()
        
        Log.d(TAG, "Memory before STL export: ${usedMemory}MB / ${maxMemory}MB ($memoryPercent%)")
        
        if (memoryPercent > 80) {
            Log.w(TAG, "WARNING: High memory usage before STL export - attempting GC")
            System.gc()
            Thread.sleep(100)
        }
        
        try {
            // Estimate size: ~200 bytes per triangle in STL format
            val estimatedSize = mesh.triangles.size * 200 + 1024
            val estimatedSizeMB = estimatedSize / (1024 * 1024)
            
            Log.d(TAG, "Estimated STL size: ${estimatedSizeMB}MB")
            
            if (estimatedSizeMB > 50) {
                Log.w(TAG, "WARNING: Large STL file (${estimatedSizeMB}MB) - may cause memory issues")
            }
            
            // Pre-allocate StringBuilder with estimated capacity
            val stl = StringBuilder(estimatedSize)
            stl.append("solid $name\n")
            
            var progressCounter = 0
            for ((index, triangle) in mesh.triangles.withIndex()) {
                // Progress logging for large exports
                if (index % 1000 == 0 && index > 0) {
                    progressCounter++
                    Log.d(TAG, "STL export progress: $index/${mesh.triangles.size} (${index * 100 / mesh.triangles.size}%)")
                    
                    // Check memory every 5000 triangles
                    if (progressCounter % 5 == 0) {
                        val currentUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                        val currentPercent = (currentUsed.toDouble() / maxMemory.toDouble() * 100).toInt()
                        if (currentPercent > 90) {
                            Log.e(TAG, "CRITICAL: Memory usage at $currentPercent% during STL export")
                            throw OutOfMemoryError("Memory exhausted during STL export at triangle $index")
                        }
                    }
                }
                
                val normal = triangle.normal()
                
                // Validate values are finite
                if (!normal.x.isFinite() || !normal.y.isFinite() || !normal.z.isFinite()) {
                    Log.w(TAG, "Skipping triangle $index with invalid normal")
                    continue
                }
                
                stl.append("  facet normal ")
                    .append(String.format("%.6f", normal.x)).append(' ')
                    .append(String.format("%.6f", normal.y)).append(' ')
                    .append(String.format("%.6f", normal.z)).append('\n')
                stl.append("    outer loop\n")
                stl.append("      vertex ")
                    .append(String.format("%.6f", triangle.v1.x)).append(' ')
                    .append(String.format("%.6f", triangle.v1.y)).append(' ')
                    .append(String.format("%.6f", triangle.v1.z)).append('\n')
                stl.append("      vertex ")
                    .append(String.format("%.6f", triangle.v2.x)).append(' ')
                    .append(String.format("%.6f", triangle.v2.y)).append(' ')
                    .append(String.format("%.6f", triangle.v2.z)).append('\n')
                stl.append("      vertex ")
                    .append(String.format("%.6f", triangle.v3.x)).append(' ')
                    .append(String.format("%.6f", triangle.v3.y)).append(' ')
                    .append(String.format("%.6f", triangle.v3.z)).append('\n')
                stl.append("    endloop\n")
                stl.append("  endfacet\n")
            }
            
            stl.append("endsolid $name\n")
            
            val finalSize = stl.length / (1024 * 1024)
            Log.d(TAG, "STL export completed: ${stl.length} characters (${finalSize}MB)")
            
            return stl.toString()
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OUT OF MEMORY during STL export", e)
            // Try to free memory
            System.gc()
            throw RuntimeException("Out of memory while exporting STL file. The mesh is too large. Try reducing quality or point count.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to STL: ${e.message}", e)
            throw RuntimeException("Failed to export STL: ${e.message}", e)
        }
    }
    
    private fun calculateBounds(points: List<Reconstruction3D.Point3D>): Pair<Reconstruction3D.Point3D, Reconstruction3D.Point3D> {
        return Pair(
            Reconstruction3D.Point3D(
                points.minOf { it.x },
                points.minOf { it.y },
                points.minOf { it.z }
            ),
            Reconstruction3D.Point3D(
                points.maxOf { it.x },
                points.maxOf { it.y },
                points.maxOf { it.z }
            )
        )
    }
}
