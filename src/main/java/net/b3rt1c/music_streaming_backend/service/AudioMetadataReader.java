package net.b3rt1c.music_streaming_backend.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern YEAR = Pattern.compile("(?:1|2)\\d{3}");

    static {
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
    }

    public AudioMetadata read(Path file, String fallbackTitle) {
        String title = fallbackTitle(fallbackTitle);
        return readStored(file)
            .map(metadata -> new AudioMetadata(
                firstNonBlank(metadata.title(), title),
                metadata.artist(),
                metadata.album(),
                metadata.durationSeconds(),
                metadata.trackNumber(),
                metadata.genre(),
                metadata.year()
            ))
            .orElseGet(() -> AudioMetadata.fallback(title));
    }

    /**
     * Empty when the file is missing or cannot be parsed. A present value may still have null tags.
     */
    public Optional<AudioMetadata> readStored(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            AudioFile audioFile = AudioFileIO.read(file.toFile());
            Tag tag = audioFile.getTag();
            return Optional.of(new AudioMetadata(
                readField(tag, FieldKey.TITLE),
                firstNonBlank(readField(tag, FieldKey.ARTIST), readField(tag, FieldKey.ALBUM_ARTIST)),
                readField(tag, FieldKey.ALBUM),
                duration(audioFile.getAudioHeader()),
                parseTrack(readField(tag, FieldKey.TRACK)),
                readField(tag, FieldKey.GENRE),
                parseYear(readField(tag, FieldKey.YEAR))
            ));
        } catch (Exception exception) {
            return Optional.empty();
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

    static Integer parseYear(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = YEAR.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group());
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
