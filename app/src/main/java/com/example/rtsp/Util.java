package com.example.rtsp;

import kotlin.text.Charsets;


// From exoplayer
public class Util {

    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /**
     * Converts an integer to a long by unsigned conversion.
     *
     * <p>This method is equivalent to {@link Integer#toUnsignedLong(int)} for API 26+.
     */
    public static long toUnsignedLong(int x) {
        // x is implicitly casted to a long before the bit operation is executed but this does not
        // impact the method correctness.
        return x & 0xFFFFFFFFL;
    }

    /**
     * Returns the long that is composed of the bits of the 2 specified integers.
     *
     * @param mostSignificantBits The 32 most significant bits of the long to return.
     * @param leastSignificantBits The 32 least significant bits of the long to return.
     * @return a long where its 32 most significant bits are {@code mostSignificantBits} bits and its
     *     32 least significant bits are {@code leastSignificantBits}.
     */
    public static long toLong(int mostSignificantBits, int leastSignificantBits) {
        return (toUnsignedLong(mostSignificantBits) << 32) | toUnsignedLong(leastSignificantBits);
    }


    /**
     * Returns a new {@link String} constructed by decoding UTF-8 encoded bytes.
     *
     * @param bytes The UTF-8 encoded bytes to decode.
     * @return The string.
     */
    public static String fromUtf8Bytes(byte[] bytes) {
        return new String(bytes, Charsets.UTF_8);
    }

    /**
     * Returns a new {@link String} constructed by decoding UTF-8 encoded bytes in a subarray.
     *
     * @param bytes The UTF-8 encoded bytes to decode.
     * @param offset The index of the first byte to decode.
     * @param length The number of bytes to decode.
     * @return The string.
     */
    public static String fromUtf8Bytes(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, Charsets.UTF_8);
    }

    /**
     * Returns a new byte array containing the code points of a {@link String} encoded using UTF-8.
     *
     * @param value The {@link String} whose bytes should be obtained.
     * @return The code points encoding using UTF-8.
     */
    public static byte[] getUtf8Bytes(String value) {
        return value.getBytes(Charsets.UTF_8);
    }


    /**
     * Returns whether the given character is a carriage return ('\r') or a line feed ('\n').
     *
     * @param c The character.
     * @return Whether the given character is a linebreak.
     */
    public static boolean isLinebreak(int c) {
        return c == '\n' || c == '\r';
    }


}
