package com.example.rtsp;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


// https://tools.ietf.org/html/rfc3640
//          +---------+-----------+-----------+---------------+
//         | RTP     | AU Header | Auxiliary | Access Unit   |
//         | Header  | Section   | Section   | Data Section  |
//         +---------+-----------+-----------+---------------+
//
//                   <----------RTP Packet Payload----------->
public class AacParser {

    private static final String TAG = AacParser.class.getSimpleName();
    private static final int MODE_LBR = 0;
    private static final int MODE_HBR = 1;
    // Number of bits for AAC AU sizes, indexed by mode (LBR and HBR)
    private static final int[] NUM_BITS_AU_SIZES = {6, 13};
    // Number of bits for AAC AU index(-delta), indexed by mode (LBR and HBR)
    private static final int[] NUM_BITS_AU_INDEX = {2, 3};
    // Frame Sizes for AAC AU fragments, indexed by mode (LBR and HBR)
    private final ParsableBitArray headerScratchBits;
    private final ParsableByteArray headerScratchBytes;
    private final int _aacMode;
    private final boolean completeFrameIndicator = true;

    private final int numBitsAuSize;
    private final int numBitsAuIndex;

    public AacParser(@NonNull String aacMode) {
        _aacMode = aacMode.equalsIgnoreCase("AAC-lbr") ? MODE_LBR : MODE_HBR;

        headerScratchBits = new ParsableBitArray();
        headerScratchBytes = new ParsableByteArray();

        numBitsAuSize = NUM_BITS_AU_SIZES[_aacMode];
        numBitsAuIndex = NUM_BITS_AU_INDEX[_aacMode];
    }

    @Nullable
    public byte[] processRtpPacketAndGetSample(@NonNull byte[] data, int length) {
//        Log.d(TAG, "processRtpPacketAndGetSample(length=" + length +" data: " + data.length + ")");
        int auHeadersCount = 1;

        // Use a ParsableBitArray to parse the AU headers
        ParsableByteArray packet = new ParsableByteArray(data, length);

//      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- .. -+-+-+-+-+-+-+-+-+-+
//      |AU-headers-length|AU-header|AU-header|      |AU-header|padding|
//      |                 |   (1)   |   (2)   |      |   (n)   | bits  |
//      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- .. -+-+-+-+-+-+-+-+-+-+
        int auHeadersLength = packet.readShort();//((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        // The auHeadersLength is in bits, convert it to bytes
        int auHeadersLengthBytes = (auHeadersLength + 7) / 8;

        headerScratchBytes.reset(auHeadersLengthBytes);
        packet.readBytes(headerScratchBytes.getData(), 0, auHeadersLengthBytes);
        headerScratchBits.reset(headerScratchBytes.getData());

        int bitsAvailable = auHeadersLength - (numBitsAuSize + numBitsAuIndex);

        if (bitsAvailable >= 0) {// && (numBitsAuSize + numBitsAuSize) > 0) {
            auHeadersCount += bitsAvailable / (numBitsAuSize + numBitsAuIndex);
        }

        // The first part of the AU header is the size of the AAC frame in bits
        int auSize = headerScratchBits.readBits(numBitsAuSize);

        if (auHeadersCount == 1) {
            // The second part of the AU header is the AU index
            int auIndex = headerScratchBits.readBits(numBitsAuIndex);

            if (completeFrameIndicator) {
                if (auIndex == 0) {
                    if (packet.bytesLeft() == auSize) {
                        return handleSingleAacFrame(packet);
                    } else {
                        return handleFragmentationAacFrame(packet, auSize);
                    }
                }
            } else {
//                Log.d(TAG, "completeFrameIndicator 1");
                return handleFragmentationAacFrame(packet, auSize);
            }
        } else {
            // here auHeadersCount >= 2
            if (completeFrameIndicator) {
                Log.d(TAG, "completeFrameIndicator 2");
                handleMultipleAacFrames(packet, auSize);
            }
        }
        return new byte[0];
    }

    private byte[] handleMultipleAacFrames(ParsableByteArray packet, int auSizeBytes) {
        ParsableBitArray auHeadersBits = new ParsableBitArray(headerScratchBytes.getData());
        byte[] frame = new byte[auSizeBytes];
        while (auHeadersBits.bitsLeft() > 0) {
            // The first part of the AU header is the size of the AAC frame in bits
            int auSize = auHeadersBits.readBits(NUM_BITS_AU_SIZES[_aacMode]);
            // Read the AAC frame from the packet
            packet.readBytes(frame, 0, auSizeBytes);
            System.arraycopy(packet.getData(), packet.getPosition(), frame, 0, auSize); // combine in frame, need test logic
            Log.d(TAG, "completeFrameIndicator multi");
        }
        return packet.getData(); // here need test if exist, but it rarely case
    }

    private byte[] handleSingleAacFrame(ParsableByteArray packet) {
        int length = packet.bytesLeft();
        byte[] data = new byte[length];
        System.arraycopy(packet.getData(), packet.getPosition(), data, 0, data.length);
        return data;
    }

    private byte[] handleFragmentationAacFrame(ParsableByteArray packet, int auSize) {
        byte[] frame = new byte[auSize];
        System.arraycopy(packet.getData(), packet.getPosition(), frame, 0, auSize);
        return frame;
    }

}
