package net.b3rt1c.music_streaming_backend;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WavFixture {
    private WavFixture() {
    }

    public static byte[] pcmWav(int sampleRate, int seconds) {
        int dataSize = sampleRate * seconds;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[] {'R', 'I', 'F', 'F'});
        buffer.putInt(36 + dataSize);
        buffer.put(new byte[] {'W', 'A', 'V', 'E'});
        buffer.put(new byte[] {'f', 'm', 't', ' '});
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate);
        buffer.putShort((short) 1);
        buffer.putShort((short) 8);
        buffer.put(new byte[] {'d', 'a', 't', 'a'});
        buffer.putInt(dataSize);
        return buffer.array();
    }
}
