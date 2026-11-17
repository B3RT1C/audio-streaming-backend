package net.b3rt1c.music_streaming_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.b3rt1c.music_streaming_backend.WavFixture;

class AudioMetadataReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesTrackNumbers() {
        assertEquals(7, AudioMetadataReader.parseTrack("7/10"));
        assertEquals(4, AudioMetadataReader.parseTrack("04"));
        assertNull(AudioMetadataReader.parseTrack("0"));
        assertNull(AudioMetadataReader.parseTrack("no"));
    }

    @Test
    void readsTagsAndFallsBackToTheFileName() throws Exception {
        Path tagged = tempDir.resolve("corazon.wav");
        Files.write(tagged, WavFixture.pcmWav(8000, 2));
        AudioFile audioFile = AudioFileIO.read(tagged.toFile());
        Tag tag = audioFile.getTagOrCreateAndSetDefault();
        tag.setField(FieldKey.TITLE, "Corazón");
        tag.setField(FieldKey.ARTIST, "José");
        tag.setField(FieldKey.ALBUM, "Álbum");
        tag.setField(FieldKey.TRACK, "7/10");
        audioFile.commit();

        AudioMetadataReader reader = new AudioMetadataReader();
        AudioMetadata metadata = reader.read(tagged, "corazon");
        assertEquals("Corazón", metadata.title());
        assertEquals("José", metadata.artist());
        assertEquals("Álbum", metadata.album());
        assertNull(metadata.genre());
        assertNull(metadata.year());
        assertTrue(metadata.durationSeconds() != null && metadata.durationSeconds() > 0);

        Path mp3 = tempDir.resolve("corazon.mp3");
        Files.write(mp3, net.b3rt1c.music_streaming_backend.Mp3Fixture.withTags("Corazón", "José", "Álbum", "7/10"));
        AudioMetadata taggedMp3 = reader.read(mp3, "corazon");
        assertEquals("Corazón", taggedMp3.title());
        assertEquals("José", taggedMp3.artist());
        assertEquals("Álbum", taggedMp3.album());
        assertEquals(7, taggedMp3.trackNumber());

        Path dated = tempDir.resolve("dated.mp3");
        Files.write(dated, net.b3rt1c.music_streaming_backend.Mp3Fixture.withTags(
            "Corazón", "José", "Álbum", "7/10", "Rock", "2010-05-01"
        ));
        AudioMetadata withYear = reader.read(dated, "dated");
        assertEquals("Rock", withYear.genre());
        assertEquals(2010, withYear.year());

        Path untagged = tempDir.resolve("nota.mp3");
        Files.write(untagged, new byte[] {1, 2, 3});
        AudioMetadata fallback = reader.read(untagged, "nota");
        assertEquals("nota", fallback.title());
        assertNull(fallback.artist());
        assertNull(fallback.durationSeconds());
        assertNull(fallback.genre());
        assertNull(fallback.year());
    }

    @Test
    void parsesYearsFromCommonTagShapes() {
        assertEquals(2010, AudioMetadataReader.parseYear("2010"));
        assertEquals(1999, AudioMetadataReader.parseYear("1999-05-01"));
        assertEquals(2011, AudioMetadataReader.parseYear("2011/2012"));
        assertNull(AudioMetadataReader.parseYear("no"));
        assertNull(AudioMetadataReader.parseYear(null));
    }
}
