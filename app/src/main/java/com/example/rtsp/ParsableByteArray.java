package com.example.rtsp;
/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Wraps a byte array, providing a set of methods for parsing data from it. Numerical values are
 * parsed with the assumption that their constituent bytes are in big endian order.
 *
 */
public final class ParsableByteArray {

    private byte[] data;
    private int position;
    // TODO(internal b/147657250): Enforce this limit on all read methods.
    private int limit;

    /** Creates a new instance that initially has no backing data. */
    public ParsableByteArray() {
        data = Util.EMPTY_BYTE_ARRAY;
    }

    /**
     * Creates a new instance that wraps an existing array.
     *
     * @param data The data to wrap.
     * @param limit The limit to set.
     */
    public ParsableByteArray(byte[] data, int limit) {
        this.data = data;
        this.limit = limit;
    }

    /**
     * Resets the position to zero and the limit to the specified value. This might replace or wipe
     * the {@link #getData() underlying array}, potentially invalidating any local references.
     *
     * @param limit The limit to set.
     */
    public void reset(int limit) {
        reset(capacity() < limit ? new byte[limit] : data, limit);
    }

    /**
     * Updates the instance to wrap {@code data}, and resets the position to zero.
     *
     * @param data The array to wrap.
     * @param limit The limit to set.
     */
    public void reset(byte[] data, int limit) {
        this.data = data;
        this.limit = limit;
        position = 0;
    }

    /** Returns the number of bytes yet to be read. */
    public int bytesLeft() {
        return limit - position;
    }

    /** Returns the limit. */
    public int limit() {
        return limit;
    }


    /** Returns the current offset in the array, in bytes. */
    public int getPosition() {
        return position;
    }

    /**
     * Returns the underlying array.
     *
     * <p>Changes to this array are reflected in the results of the {@code read...()} methods.
     *
     * <p>This reference must be assumed to become invalid when {@link #reset} or {@link
     * #ensureCapacity} are called (because the array might get reallocated).
     */
    public byte[] getData() {
        return data;
    }

    /** Returns the capacity of the array, which may be larger than the limit. */
    public int capacity() {
        return data.length;
    }

    /**
     * Reads the next {@code length} bytes into {@code buffer} at {@code offset}.
     *
     * @see System#arraycopy(Object, int, Object, int, int)
     * @param buffer The array into which the read data should be written.
     * @param offset The offset in {@code buffer} at which the read data should be written.
     * @param length The number of bytes to read.
     */
    public void readBytes(byte[] buffer, int offset, int length) {
        System.arraycopy(data, position, buffer, offset, length);
        position += length;
    }

    /** Reads the next two bytes as a signed value. */
    public short readShort() {
        return (short) ((data[position++] & 0xFF) << 8 | (data[position++] & 0xFF));
    }

}
