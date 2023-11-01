package com.example.rtsp;

import androidx.annotation.Nullable;

public class VideoCodecUtils {

    public static final byte NAL_IDR_SLICE = 5;
    public static final byte NAL_SPS = 7;
    public static final byte NAL_PPS = 8;

    private static final byte[] NAL_PREFIX1 = {0x00, 0x00, 0x00, 0x01};
    private static final byte[] NAL_PREFIX2 = {0x00, 0x00, 0x01};

    public static byte getH264NalUnitType(@Nullable byte[] data, int offset, int length) {
        if (data == null || length <= NAL_PREFIX1.length)
            return (byte) -1;

        int nalUnitTypeOctetOffset = -1;
        if (data[offset + NAL_PREFIX2.length - 1] == 1)
            nalUnitTypeOctetOffset = offset + NAL_PREFIX2.length - 1;
        else if (data[offset + NAL_PREFIX1.length - 1] == 1)
            nalUnitTypeOctetOffset = offset + NAL_PREFIX1.length - 1;

        if (nalUnitTypeOctetOffset != -1) {
            byte nalUnitTypeOctet = data[nalUnitTypeOctetOffset + 1];
            return (byte) (nalUnitTypeOctet & 0x1f);
        } else {
            return (byte) -1;
        }
    }

}
