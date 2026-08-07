package com.jpitsg.sysman;

import android.graphics.Rect;

import java.io.IOException;
import java.io.OutputStream;

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
    static final int ENCODING_DESKTOP_SIZE = -223;

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
