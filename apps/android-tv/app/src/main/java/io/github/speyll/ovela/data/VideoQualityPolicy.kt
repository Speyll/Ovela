package io.github.speyll.ovela.data

/**
 * Quality choices are derived from the renditions the current manifest really
 * announces. A fixed-bitrate URL exposes one variant (or none), so callers can
 * use [options] emptiness to decide whether a quality control is honest.
 */
object VideoQualityPolicy {
    fun options(heights: List<Int>): List<Int> =
        heights.filter { it > 0 }.distinct().sortedDescending()

    fun supportsSelection(heights: List<Int>): Boolean = options(heights).size > 1
}
