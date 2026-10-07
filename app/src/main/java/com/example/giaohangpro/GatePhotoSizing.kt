package com.example.giaohangpro

internal fun gatePhotoSampleSize(width: Int, height: Int, maxSide: Int = 1280): Int {
    require(maxSide > 0)
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while ((maxOf(width, height).toLong() + sample - 1) / sample > maxSide) {
        sample *= 2
    }
    return sample
}
