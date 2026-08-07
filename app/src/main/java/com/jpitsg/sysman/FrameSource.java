package com.jpitsg.sysman;

/**
 * A source of screen frames for the VNC server. Implemented once per capture
 * engine so the rest of the pipeline never has to know which one is running.
 */
interface FrameSource {

    /**
     * One captured frame.
     *
     * <p>{@link #pixels} belongs to the source and is overwritten on a later
     * {@link FrameSource#acquire}. Sources double-buffer, so a frame stays
     * readable for exactly one more acquire — long enough to diff against its
     * successor, and no longer. Anything that needs to outlive that must copy.
     */
    final class Frame {
        final int[] pixels;
        final int width;
        final int height;
        final long capturedAtMillis;

        Frame(int[] pixels, int width, int height, long capturedAtMillis) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
            this.capturedAtMillis = capturedAtMillis;
        }
    }

    /**
     * Brings the engine up at the requested scale.
     *
     * @param maxFps an upper bound the engine paces itself to. An engine with a
     *               lower ceiling of its own keeps the lower one.
     * @return false when it could not start; {@link #blockedReason} says why.
     */
    boolean start(int scalePercent, int maxFps);

    /**
     * Blocks for the next frame, honouring the engine's own frame-rate limit.
     *
     * @return null on timeout or a failed capture; the source stays usable.
     */
    Frame acquire(long timeoutMillis);

    /** Frame width after scaling; 0 before {@link #start}. */
    int width();

    /** Frame height after scaling; 0 before {@link #start}. */
    int height();

    /** Display width before scaling; input coordinates map back onto this. */
    int sourceWidth();

    /** Display height before scaling. */
    int sourceHeight();

    /** True when the last acquire found the display a different size. */
    boolean consumeSizeChanged();

    /** Why the engine cannot run, or "" when it is healthy. */
    String blockedReason();

    void stop();
}
