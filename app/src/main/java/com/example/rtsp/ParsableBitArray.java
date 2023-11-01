package com.example.rtsp;


/**
 * Wraps a byte array, providing methods that allow it to be read as a bitstream.
 *
 */
public final class ParsableBitArray {

    public byte[] data;

    private int byteOffset;
    private int bitOffset;
    private int byteLimit;

    /** Creates a new instance that initially has no backing data. */
    public ParsableBitArray() {
        data = Util.EMPTY_BYTE_ARRAY;
    }

    /**
     * Creates a new instance that wraps an existing array.
     *
     * @param data The data to wrap.
     */
    public ParsableBitArray(byte[] data) {
        this(data, data.length);
    }

    /**
     * Creates a new instance that wraps an existing array.
     *
     * @param data The data to wrap.
     * @param limit The limit in bytes.
     */
    public ParsableBitArray(byte[] data, int limit) {
        this.data = data;
        byteLimit = limit;
    }

    /**
     * Updates the instance to wrap {@code data}, and resets the position to zero.
     *
     * @param data The array to wrap.
     */
    public void reset(byte[] data) {
        reset(data, data.length);
    }

    /**
     * Updates the instance to wrap {@code data}, and resets the position to zero.
     *
     * @param data The array to wrap.
     * @param limit The limit in bytes.
     */
    public void reset(byte[] data, int limit) {
        this.data = data;
        byteOffset = 0;
        bitOffset = 0;
        byteLimit = limit;
    }

    /** Returns the number of bits yet to be read. */
    public int bitsLeft() {
        return (byteLimit - byteOffset) * 8 - bitOffset;
    }

    /**
     * Reads up to 32 bits.
     *
     * @param numBits The number of bits to read.
     * @return An integer whose bottom {@code numBits} bits hold the read data.
     */
    public int readBits(int numBits) {
        if (numBits == 0) {
            return 0;
        }
        int returnValue = 0;
        bitOffset += numBits;
        while (bitOffset > 8) {
            bitOffset -= 8;
            returnValue |= (data[byteOffset++] & 0xFF) << bitOffset;
        }
        returnValue |= (data[byteOffset] & 0xFF) >> (8 - bitOffset);
        returnValue &= 0xFFFFFFFF >>> (32 - numBits);
        if (bitOffset == 8) {
            bitOffset = 0;
            byteOffset++;
        }
        return returnValue;
    }

}
