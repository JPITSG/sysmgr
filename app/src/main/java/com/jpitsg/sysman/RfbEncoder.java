package com.jpitsg.sysman;

import android.graphics.Rect;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.Deflater;

/**
 * Writes framebuffer rectangles in the client's requested pixel format.
 *
 * <p>Raw only for now; the compressed encodings come later. What is already
 * here is the format conversion, because clients do not all ask for the
 * server's native layout — a 16-bit request served as 32-bit true colour
 * produces a scrambled screen that looks exactly like an encoder bug, and
 * chasing that later is worse than the forty lines it takes to convert.
 */
final class RfbEncoder {
    static final int ENCODING_RAW = 0;
    static final int ENCODING_COPY_RECT = 1;
    static final int ENCODING_ZRLE = 16;
    static final int ENCODING_DESKTOP_SIZE = -223;

    /** ZRLE always works in 64x64 tiles, whatever the rectangle's shape. */
    private static final int ZRLE_TILE = 64;
    /** Above this many distinct colours a tile is cheaper sent raw than as a palette. */
    private static final int MAX_PALETTE = 16;

    /** The wire layout of one pixel, as negotiated by SetPixelFormat. */
    static final class PixelFormat {
        final int bitsPerPixel;
        final int depth;
        final boolean bigEndian;
        final boolean trueColour;
        final int redMax;
        final int greenMax;
        final int blueMax;
        final int redShift;
        final int greenShift;
        final int blueShift;
        /** True when a source pixel can go on the wire without arithmetic. */
        final boolean nativeLayout;

        PixelFormat(int bitsPerPixel, int depth, boolean bigEndian, boolean trueColour,
                    int redMax, int greenMax, int blueMax,
                    int redShift, int greenShift, int blueShift) {
            this.bitsPerPixel = bitsPerPixel;
            this.depth = depth;
            this.bigEndian = bigEndian;
            this.trueColour = trueColour;
            this.redMax = redMax;
            this.greenMax = greenMax;
            this.blueMax = blueMax;
            this.redShift = redShift;
            this.greenShift = greenShift;
            this.blueShift = blueShift;
            this.nativeLayout = bitsPerPixel == 32 && !bigEndian && trueColour
                    && redMax == 255 && greenMax == 255 && blueMax == 255
                    && redShift == 16 && greenShift == 8 && blueShift == 0;
        }

        /**
         * What the server offers in ServerInit. ARGB_8888 ints written
         * little-endian land as B,G,R,unused, which is exactly shifts 16/8/0
         * with the big-endian flag clear — so the common case needs no
         * conversion at all.
         */
        static PixelFormat serverDefault() {
            return new PixelFormat(32, 24, false, true, 255, 255, 255, 16, 8, 0);
        }

        int bytesPerPixel() {
            return Math.max(1, bitsPerPixel / 8);
        }

        /**
         * ZRLE's "compressed pixel": three bytes instead of four whenever the
         * fourth carries nothing, which is the usual case for 32bpp depth-24.
         * A quarter off the wire before zlib even sees it.
         */
        int cpixelBytes() {
            if (bitsPerPixel == 32 && depth <= 24 && (colourMask() & 0xFF000000) == 0) {
                return 3;
            }
            return bytesPerPixel();
        }

        private int colourMask() {
            return (redMax << redShift) | (greenMax << greenShift) | (blueMax << blueShift);
        }

        boolean supported() {
            return trueColour && (bitsPerPixel == 8 || bitsPerPixel == 16 || bitsPerPixel == 32);
        }

        void write(OutputStream out) throws IOException {
            out.write(bitsPerPixel);
            out.write(depth);
            out.write(bigEndian ? 1 : 0);
            out.write(trueColour ? 1 : 0);
            writeShort(out, redMax);
            writeShort(out, greenMax);
            writeShort(out, blueMax);
            out.write(redShift);
            out.write(greenShift);
            out.write(blueShift);
            out.write(0);
            out.write(0);
            out.write(0);
        }

        @Override
        public String toString() {
            return bitsPerPixel + "bpp depth" + depth + (bigEndian ? " BE" : " LE")
                    + (trueColour ? " true" : " mapped")
                    + " max " + redMax + "/" + greenMax + "/" + blueMax
                    + " shift " + redShift + "/" + greenShift + "/" + blueShift;
        }
    }

    private PixelFormat format = PixelFormat.serverDefault();
    private byte[] rowBuffer = new byte[0];
    private long bytesWritten;

    /**
     * One zlib stream for the whole connection, as ZRLE requires — the history
     * it accumulates is most of where the compression comes from. Losing sync
     * on it cannot be recovered, so nothing resets it mid-session.
     */
    private final Deflater deflater = new Deflater(Deflater.BEST_SPEED, false);
    private byte[] compressed = new byte[1 << 16];
    private int compressedLength;
    private byte[] tileBuffer = new byte[1 << 14];
    private int tileLength;
    private final int[] palette = new int[MAX_PALETTE];
    private byte[] paletteIndices = new byte[ZRLE_TILE * ZRLE_TILE];

    void setFormat(PixelFormat format) {
        this.format = format;
    }

    PixelFormat format() {
        return format;
    }

    long bytesWritten() {
        return bytesWritten;
    }

    /** Header for one rectangle: position, size, then the encoding it uses. */
    void writeRectHeader(OutputStream out, Rect rect, int encoding) throws IOException {
        writeShort(out, rect.left);
        writeShort(out, rect.top);
        writeShort(out, rect.width());
        writeShort(out, rect.height());
        writeInt(out, encoding);
        bytesWritten += 12;
    }

    /**
     * Raw pixel data for one rectangle, row by row.
     *
     * @param pixels ARGB_8888, {@code stride} ints per row
     */
    void writeRaw(OutputStream out, int[] pixels, int stride, Rect rect) throws IOException {
        int bytesPerPixel = format.bytesPerPixel();
        int rowBytes = rect.width() * bytesPerPixel;
        if (rowBuffer.length < rowBytes) {
            rowBuffer = new byte[rowBytes];
        }

        for (int y = rect.top; y < rect.bottom; y++) {
            int source = y * stride + rect.left;
            int offset = 0;
            if (format.nativeLayout) {
                for (int x = 0; x < rect.width(); x++) {
                    int argb = pixels[source + x];
                    rowBuffer[offset++] = (byte) argb;
                    rowBuffer[offset++] = (byte) (argb >> 8);
                    rowBuffer[offset++] = (byte) (argb >> 16);
                    rowBuffer[offset++] = 0;
                }
            } else {
                for (int x = 0; x < rect.width(); x++) {
                    offset = putPixel(rowBuffer, offset, convert(pixels[source + x]), bytesPerPixel);
                }
            }
            out.write(rowBuffer, 0, rowBytes);
            bytesWritten += rowBytes;
        }
    }

    // ---- ZRLE ---------------------------------------------------------------

    /**
     * ZRLE for one rectangle: 64x64 tiles fed through a zlib stream that lives
     * for the whole connection, so the compressor keeps learning the screen.
     *
     * <p>Only three of the subencodings are emitted — solid, packed palette and
     * raw. That is a server's choice to make; a client must decode all of them
     * either way. The RLE variants would help on gradients, which phone UI has
     * little of, and each one is another chance to corrupt a stream that has no
     * way to resynchronise.
     */
    void writeZrle(OutputStream out, int[] pixels, int stride, Rect rect) throws IOException {
        compressedLength = 0;
        for (int tileY = rect.top; tileY < rect.bottom; tileY += ZRLE_TILE) {
            int tileHeight = Math.min(ZRLE_TILE, rect.bottom - tileY);
            for (int tileX = rect.left; tileX < rect.right; tileX += ZRLE_TILE) {
                int tileWidth = Math.min(ZRLE_TILE, rect.right - tileX);
                buildTile(pixels, stride, tileX, tileY, tileWidth, tileHeight);
                compress(tileBuffer, tileLength, false);
            }
        }
        // Flushed per rectangle so the client can decode it without waiting for
        // whatever comes next.
        compress(tileBuffer, 0, true);

        writeInt(out, compressedLength);
        out.write(compressed, 0, compressedLength);
        bytesWritten += 4 + compressedLength;
    }

    /** Lays one tile out uncompressed into {@link #tileBuffer}. */
    private void buildTile(int[] pixels, int stride, int tileX, int tileY, int width, int height) {
        int count = width * height;
        ensureTileCapacity(1 + MAX_PALETTE * 4 + count * 4);
        if (paletteIndices.length < count) {
            paletteIndices = new byte[count];
        }

        int paletteSize = 0;
        boolean tooManyColours = false;
        for (int y = 0; y < height; y++) {
            int source = (tileY + y) * stride + tileX;
            for (int x = 0; x < width; x++) {
                int pixel = convertIfNeeded(pixels[source + x]);
                int index = -1;
                for (int i = 0; i < paletteSize; i++) {
                    if (palette[i] == pixel) {
                        index = i;
                        break;
                    }
                }
                if (index < 0) {
                    if (paletteSize == MAX_PALETTE) {
                        tooManyColours = true;
                        break;
                    }
                    palette[paletteSize] = pixel;
                    index = paletteSize++;
                }
                paletteIndices[y * width + x] = (byte) index;
            }
            if (tooManyColours) {
                break;
            }
        }

        tileLength = 0;
        if (!tooManyColours && paletteSize == 1) {
            tileBuffer[tileLength++] = 1;
            putCPixel(palette[0]);
            return;
        }
        if (!tooManyColours) {
            tileBuffer[tileLength++] = (byte) paletteSize;
            for (int i = 0; i < paletteSize; i++) {
                putCPixel(palette[i]);
            }
            packIndices(paletteSize, width, height);
            return;
        }

        // Raw, but still as CPIXELs — the fourth byte would only be padding.
        tileBuffer[tileLength++] = 0;
        for (int y = 0; y < height; y++) {
            int source = (tileY + y) * stride + tileX;
            for (int x = 0; x < width; x++) {
                putCPixel(convertIfNeeded(pixels[source + x]));
            }
        }
    }

    /**
     * Palette indices, most significant bit first, each row padded out to a
     * whole byte.
     */
    private void packIndices(int paletteSize, int width, int height) {
        int bitsPerIndex = paletteSize == 2 ? 1 : (paletteSize <= 4 ? 2 : 4);
        for (int y = 0; y < height; y++) {
            int accumulator = 0;
            int bits = 0;
            for (int x = 0; x < width; x++) {
                accumulator = (accumulator << bitsPerIndex) | paletteIndices[y * width + x];
                bits += bitsPerIndex;
                if (bits == 8) {
                    tileBuffer[tileLength++] = (byte) accumulator;
                    accumulator = 0;
                    bits = 0;
                }
            }
            if (bits > 0) {
                tileBuffer[tileLength++] = (byte) (accumulator << (8 - bits));
            }
        }
    }

    private void putCPixel(int value) {
        int bytes = format.cpixelBytes();
        if (bytes == 3) {
            if (format.bigEndian) {
                tileBuffer[tileLength++] = (byte) (value >> 16);
                tileBuffer[tileLength++] = (byte) (value >> 8);
                tileBuffer[tileLength++] = (byte) value;
            } else {
                tileBuffer[tileLength++] = (byte) value;
                tileBuffer[tileLength++] = (byte) (value >> 8);
                tileBuffer[tileLength++] = (byte) (value >> 16);
            }
            return;
        }
        tileLength = putPixel(tileBuffer, tileLength, value, bytes);
    }

    private void compress(byte[] input, int length, boolean flush) {
        if (length > 0) {
            deflater.setInput(input, 0, length);
            while (!deflater.needsInput()) {
                ensureCompressedCapacity();
                int written = deflater.deflate(compressed, compressedLength,
                        compressed.length - compressedLength);
                if (written == 0) {
                    break;
                }
                compressedLength += written;
            }
        }
        if (!flush) {
            return;
        }
        while (true) {
            ensureCompressedCapacity();
            int written = deflater.deflate(compressed, compressedLength,
                    compressed.length - compressedLength, Deflater.SYNC_FLUSH);
            if (written == 0) {
                return;
            }
            compressedLength += written;
        }
    }

    private void ensureCompressedCapacity() {
        if (compressed.length - compressedLength >= 4096) {
            return;
        }
        compressed = Arrays.copyOf(compressed, Math.max(compressed.length * 2, compressedLength + 8192));
    }

    private void ensureTileCapacity(int needed) {
        if (tileBuffer.length < needed) {
            tileBuffer = new byte[needed];
        }
    }

    /** Skips the arithmetic when the client asked for the layout we already have. */
    private int convertIfNeeded(int argb) {
        return format.nativeLayout ? (argb & 0x00FFFFFF) : convert(argb);
    }

    /** Ends the zlib stream; the encoder is unusable afterwards. */
    void close() {
        deflater.end();
    }

    /** Scales each channel into the client's range and puts it at its shift. */
    private int convert(int argb) {
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return ((red * format.redMax / 255) << format.redShift)
                | ((green * format.greenMax / 255) << format.greenShift)
                | ((blue * format.blueMax / 255) << format.blueShift);
    }

    private int putPixel(byte[] buffer, int offset, int value, int bytesPerPixel) {
        if (format.bigEndian) {
            for (int i = bytesPerPixel - 1; i >= 0; i--) {
                buffer[offset++] = (byte) (value >> (8 * i));
            }
        } else {
            for (int i = 0; i < bytesPerPixel; i++) {
                buffer[offset++] = (byte) (value >> (8 * i));
            }
        }
        return offset;
    }

    static void writeShort(OutputStream out, int value) throws IOException {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    static void writeInt(OutputStream out, int value) throws IOException {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
