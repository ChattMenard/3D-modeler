package com.medical.cmtcast.processing

import android.content.Context
import android.util.Log
import com.medical.cmtcast.settings.AppSettings
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
        Log.d(TAG, "Applying thickness: ${thickness}mm")
        val thickenedTriangles = mesh.triangles.map { triangle ->
            val normal = triangle.normal()
            
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
        
        return mesh.copy(triangles = thickenedTriangles)
    }
    
    /**
     * Export mesh to STL format (ASCII)
     */
    fun exportToSTL(mesh: Mesh, name: String = "leg_cast"): String {
        val stl = StringBuilder()
        stl.append("solid $name\n")
        
        for (triangle in mesh.triangles) {
            val normal = triangle.normal()
            stl.append("  facet normal ${normal.x} ${normal.y} ${normal.z}\n")
            stl.append("    outer loop\n")
            stl.append("      vertex ${triangle.v1.x} ${triangle.v1.y} ${triangle.v1.z}\n")
            stl.append("      vertex ${triangle.v2.x} ${triangle.v2.y} ${triangle.v2.z}\n")
            stl.append("      vertex ${triangle.v3.x} ${triangle.v3.y} ${triangle.v3.z}\n")
            stl.append("    endloop\n")
            stl.append("  endfacet\n")
        }
        
        stl.append("endsolid $name\n")
        
        Log.d(TAG, "Generated STL with ${mesh.triangles.size} facets")
        return stl.toString()
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
