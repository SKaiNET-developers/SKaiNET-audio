package sk.ainet.audio.core

/**
 * A neutral 2-D feature container (row-major `[rows, cols]`) so audio preprocessing never imports
 * a tensor library. For mel spectrograms rows = mel bins, cols = time frames.
 */
public class FeatureFrames(
    public val rows: Int,
    public val cols: Int,
    public val data: FloatArray,
) {
    init { require(data.size == rows * cols) { "FeatureFrames size ${data.size} != $rows*$cols" } }

    public operator fun get(row: Int, col: Int): Float = data[row * cols + col]
}

/** A feature-extraction front-end: mono 16 kHz PCM in `[-1, 1]` → [FeatureFrames]. */
public fun interface FeatureExtractor {
    public fun extract(pcm16k: FloatArray): FeatureFrames
}
