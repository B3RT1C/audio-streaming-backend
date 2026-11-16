package net.b3rt1c.music_streaming_backend;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class Mp3Fixture {
    private Mp3Fixture() {
    }

    public static byte[] withTags(String title, String artist, String album, String track) {
        byte[] body = concat(
            frame("TIT2", title),
            frame("TPE1", artist),
            frame("TALB", album),
            frame("TRCK", track)
        );
        ByteBuffer file = ByteBuffer.allocate(10 + body.length + 417 * 2);
        file.put(new byte[] {'I', 'D', '3', 3, 0, 0});
        file.put((byte) ((body.length >> 21) & 0x7F));
        file.put((byte) ((body.length >> 14) & 0x7F));
        file.put((byte) ((body.length >> 7) & 0x7F));
        file.put((byte) (body.length & 0x7F));
        file.put(body);
        byte[] mpeg = mpegFrame();
        file.put(mpeg);
        file.put(mpeg);
        return file.array();
    }

    private static byte[] frame(String id, String text) {
        byte[] data = text.getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer buffer = ByteBuffer.allocate(11 + data.length);
        buffer.put(id.getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(1 + data.length);
        buffer.putShort((short) 0);
        buffer.put((byte) 0);
        buffer.put(data);
        return buffer.array();
    }

    private static byte[] mpegFrame() {
        byte[] frame = new byte[417];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        frame[2] = (byte) 0x90;
        return frame;
    }

    private static byte[] concat(byte[]... parts) {
        int size = 0;
        for (byte[] part : parts) {
            size += part.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        for (byte[] part : parts) {
            buffer.put(part);
        }
        return buffer.array();
    }
}
