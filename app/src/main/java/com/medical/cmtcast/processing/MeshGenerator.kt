package com.medical.cmtcast.processing

import android.util.Log
import kotlin.math.*

/**
 * Generates 3D mesh from point cloud and exports to STL
 */
class MeshGenerator {
    
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
        
        val triangles = mutableListOf<Triangle>()
        
        // Sort points by height (Y axis) to create layers
        val sortedPoints = points.sortedBy { it.y }
        
        // Create horizontal slices
        val sliceHeight = 5.0 // mm between slices
        val minY = sortedPoints.first().y
        val maxY = sortedPoints.last().y
        val numSlices = ((maxY - minY) / sliceHeight).toInt()
        
        Log.d(TAG, "Creating $numSlices horizontal slices")
        
        for (i in 0 until numSlices - 1) {
            val y1 = minY + i * sliceHeight
            val y2 = minY + (i + 1) * sliceHeight
            
            // Get points in each slice
            val slice1 = sortedPoints.filter { it.y >= y1 && it.y < y1 + sliceHeight }
            val slice2 = sortedPoints.filter { it.y >= y2 && it.y < y2 + sliceHeight }
            
            if (slice1.size >= 3 && slice2.size >= 3) {
                // Sort points in each slice by angle around center
                val center1 = calculateCenter(slice1)
                val center2 = calculateCenter(slice2)
                
                val sorted1 = sortByAngle(slice1, center1)
                val sorted2 = sortByAngle(slice2, center2)
                
                // Connect the two slices with triangles
                triangles.addAll(connectSlices(sorted1, sorted2))
            }
        }
        
        val bounds = calculateBounds(points)
        
        Log.d(TAG, "Generated ${triangles.size} triangles")
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
        
        // Create triangle strip between slices
        var i1 = 0
        var i2 = 0
        
        while (i1 < n1 && i2 < n2) {
            val p1 = slice1[i1]
            val p2 = slice1[(i1 + 1) % n1]
            val p3 = slice2[i2]
            val p4 = slice2[(i2 + 1) % n2]
            
            // Calculate distances to decide which diagonal to use
            val d1 = distance(p1, p4)
            val d2 = distance(p2, p3)
            
            if (d1 < d2) {
                triangles.add(Triangle(p1, p3, p4))
                triangles.add(Triangle(p1, p4, p2))
                i1++
                i2++
            } else {
                triangles.add(Triangle(p1, p3, p2))
                triangles.add(Triangle(p2, p3, p4))
                i1++
                i2++
            }
        }
        
        return triangles
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
     * Apply offset to mesh for cast thickness
     */
    fun applyThickness(mesh: Mesh, thickness: Double = 3.0): Mesh {
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
