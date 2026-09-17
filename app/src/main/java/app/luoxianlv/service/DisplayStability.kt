package app.luoxianlv.service

internal class DisplayStability {
    private var last: Triple<Int, Int, Int>? = null
    private var since = 0L

    fun reset() { last = null }

    fun ready(sample: Triple<Int, Int, Int>, now: Long): Boolean {
        if (sample.first <= 0 || sample.second <= 0) {
            reset()
            return false
        }
        if (sample != last) {
            last = sample
            since = now
            return false
        }
        return now - since >= 240
    }
}
