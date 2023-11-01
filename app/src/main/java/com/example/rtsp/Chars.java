package com.example.rtsp;

/*
 * Copyright (C) 2008 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */


import java.util.Arrays;

// From exoplayer

/**
 * Static utility methods pertaining to {@code char} primitives, that are not already found in
 * either {@link Character} or {@link Arrays}.
 *
 * <p>All the operations in this class treat {@code char} values strictly numerically; they are
 * neither Unicode-aware nor locale-dependent.
 *
 * <p>See the Guava User Guide article on <a
 * href="https://github.com/google/guava/wiki/PrimitivesExplained">primitive utilities</a>.
 *
 * @author Kevin Bourrillion
 * @since 1.0
 */
public final class Chars {
    private Chars() {
    }

    /**
     * Returns the {@code char} value that is equal to {@code value}, if possible.
     *
     * @param value any value in the range of the {@code char} type
     * @return the {@code char} value that equals {@code value}
     * @throws IllegalArgumentException if {@code value} is greater than {@link Character#MAX_VALUE}
     *                                  or less than {@link Character#MIN_VALUE}
     */
    public static char checkedCast(long value) {
        char result = (char) value;
        return result;
    }

    /**
     * Returns {@code true} if {@code target} is present as an element anywhere in {@code array}.
     *
     * @param array  an array of {@code char} values, possibly empty
     * @param target a primitive {@code char} value
     * @return {@code true} if {@code array[i] == target} for some value of {@code i}
     */
    public static boolean contains(char[] array, char target) {
        for (char value : array) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the {@code char} value whose byte representation is the given 2 bytes, in big-endian
     * order; equivalent to {@code Chars.fromByteArray(new byte[] {b1, b2})}.
     *
     * @since 7.0
     */
    public static char fromBytes(byte b1, byte b2) {
        return (char) ((b1 << 8) | (b2 & 0xFF));
    }

}
