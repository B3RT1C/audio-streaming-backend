package net.b3rt1c.music_streaming_backend.service;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.springframework.stereotype.Service;

import net.b3rt1c.music_streaming_backend.util.Names;

@Service
public class AudioMetadataReader {
    private static final int MAX_TEXT = 500;

    static {
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
    }

    public AudioMetadata read(Path file, String fallbackTitle) {
        String title = fallbackTitle(fallbackTitle);
        try {
            AudioFile audioFile = AudioFileIO.read(file.toFile());
            Tag tag = audioFile.getTag();
            return new AudioMetadata(
                firstNonBlank(readField(tag, FieldKey.TITLE), title),
                firstNonBlank(readField(tag, FieldKey.ARTIST), readField(tag, FieldKey.ALBUM_ARTIST)),
                readField(tag, FieldKey.ALBUM),
                duration(audioFile.getAudioHeader()),
                parseTrack(readField(tag, FieldKey.TRACK))
            );
        } catch (Exception exception) {
            return AudioMetadata.fallback(title);
        }
    }

    private static String readField(Tag tag, FieldKey key) {
        if (tag == null) {
            return null;
        }
        try {
            return Names.clip(tag.getFirst(key), MAX_TEXT);
        } catch (Exception exception) {
            return null;
        }
    }

    private static Integer duration(AudioHeader header) {
        if (header == null) {
            return null;
        }
        try {
            int seconds = header.getTrackLength();
            return seconds > 0 ? seconds : null;
        } catch (Exception exception) {
            return null;
        }
    }

    static Integer parseTrack(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String number = raw.trim();
        int slash = number.indexOf('/');
        if (slash >= 0) {
            number = number.substring(0, slash).trim();
        }
        try {
            int value = Integer.parseInt(number);
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static String fallbackTitle(String fallbackTitle) {
        String clipped = Names.clip(fallbackTitle, MAX_TEXT);
        return clipped == null ? "audio" : clipped;
    }
}
