package net.b3rt1c.music_streaming_backend;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.repository.ArtistRepository;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistItemRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistRepository;
import net.b3rt1c.music_streaming_backend.repository.TagRepository;
import net.b3rt1c.music_streaming_backend.service.AudioFileService;

@SpringBootTest
@AutoConfigureMockMvc
class TypedTagApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AudioDataRepository audioDataRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private PlaylistItemRepository playlistItemRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private AudioFileService audioFileService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM audio_tags");
        jdbc.update("DELETE FROM artist_tags");
        jdbc.update("DELETE FROM playlist_tags");
        playlistItemRepository.deleteAll();
        playlistRepository.deleteAll();
        audioDataRepository.deleteAll();
        artistRepository.deleteAll();
        tagRepository.deleteAll();
        jdbc.update("DELETE FROM tag_types WHERE is_system = FALSE");
    }

    @Test
    void systemTypesAreFixedAndUserTypesCanBeRenamedAndDeleted() throws Exception {
        mockMvc.perform(get("/tag-types"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].key", contains("artist", "genre", "album", "year", "language", "free")))
            .andExpect(jsonPath("$[0].system").value(true))
            .andExpect(jsonPath("$[0].name").value("Artista"))
            .andExpect(jsonPath("$[1].name").value("Género"))
            .andExpect(jsonPath("$[5].name").value("Libre"))
            .andExpect(jsonPath("$[0].count").value(0));

        int genreId = typeId("genre");
        mockMvc.perform(patch("/tag-types/" + genreId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Estilo\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
        mockMvc.perform(patch("/tag-types/" + genreId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isConflict());
        mockMvc.perform(delete("/tag-types/" + genreId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
        mockMvc.perform(delete("/tag-types/" + typeId("free")))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/tag-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Género\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
        mockMvc.perform(post("/tag-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_BODY"));

        mockMvc.perform(post("/tag-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mood\",\"color\":\"#112233\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value("mood"))
            .andExpect(jsonPath("$.name").value("Mood"))
            .andExpect(jsonPath("$.system").value(false))
            .andExpect(jsonPath("$.color").value("#112233"))
            .andExpect(jsonPath("$.count").value(0));

        int moodId = typeId("mood");
        mockMvc.perform(patch("/tag-types/" + moodId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Energía\",\"color\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").value("mood"))
            .andExpect(jsonPath("$.name").value("Energía"))
            .andExpect(jsonPath("$.color").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(post("/tag-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"energia\"}"))
            .andExpect(status().isConflict());
        mockMvc.perform(patch("/tag-types/" + moodId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"" + "x".repeat(33) + "\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/tag-types/" + moodId))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/tag-types"))
            .andExpect(jsonPath("$[*].key", contains("artist", "genre", "album", "year", "language", "free")));
        mockMvc.perform(delete("/tag-types/999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void derivedTagsCannotBeAssignedAndStayWhenThePutOmitsThem() throws Exception {
        int id = upload("corazon.mp3", "Corazón", "José", "Álbum", "Rock", "2010");
        mockMvc.perform(get("/audios"))
            .andExpect(jsonPath("$[0].genre").value("Rock"))
            .andExpect(jsonPath("$[0].year").value(2010))
            .andExpect(jsonPath("$[0].title").value("Corazón"))
            .andExpect(jsonPath("$[0].artist").value("José"))
            .andExpect(jsonPath("$[0].tags", containsInAnyOrder("2010", "Rock", "Álbum")));

        mockMvc.perform(put("/audios/" + id + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\"Live\",{\"type\":\"idioma\",\"name\":\"Español\"}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags", containsInAnyOrder("2010", "Español", "Live", "Rock", "Álbum")));

        String before = tagsOf(id);
        mockMvc.perform(put("/audios/" + id + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[{\"type\":\"genre\",\"name\":\"Metal\"}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_BODY"));
        mockMvc.perform(put("/audios/" + id + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[{\"type\":\"álbum\",\"name\":\"Otro\"}]}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/audios/" + id + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[{\"type\":\"year\",\"name\":\"1999\"}]}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/audios/" + id + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[{\"type\":\"artist\",\"name\":\"Ana\"}]}"))
            .andExpect(status().isBadRequest());
        assertEquals(before, tagsOf(id));

        mockMvc.perform(post("/tag-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mood\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(put("/audios/" + id + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[{\"type\":\"mood\",\"name\":\"gym\"},{\"type\":\"Energía\",\"name\":\"no\"}]}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/audios/" + id + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[{\"type\":\"mood\",\"name\":\"gym\"}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags", containsInAnyOrder("2010", "gym", "Rock", "Álbum")));

        mockMvc.perform(put("/audios/" + id + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags", containsInAnyOrder("2010", "Rock", "Álbum")));

        mockMvc.perform(get("/tags").param("type", "genre"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("Rock"))
            .andExpect(jsonPath("$[0].readOnly").value(true))
            .andExpect(jsonPath("$[0].type.key").value("genre"))
            .andExpect(jsonPath("$[0].type.system").value(true));
        mockMvc.perform(get("/tags").param("type", "género"))
            .andExpect(jsonPath("$[0].name").value("Rock"));
        mockMvc.perform(get("/tags").param("type", "free"))
            .andExpect(jsonPath("$[?(@.name == 'Rock')]").isEmpty());

        mockMvc.perform(put("/audios/" + id + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\"Live\"]}"))
            .andExpect(status().isOk());
        int moodId = typeId("mood");
        mockMvc.perform(put("/audios/" + id + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\"Live\",{\"type\":\"mood\",\"name\":\"gym\"}]}"))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/tag-types/" + moodId))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/audios"))
            .andExpect(jsonPath("$[0].tags", containsInAnyOrder("2010", "Live", "Rock", "Álbum")));
        mockMvc.perform(get("/tags").param("type", "mood"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/tags").param("type", "libre"))
            .andExpect(jsonPath("$[?(@.name == 'Live')].readOnly", contains(false)))
            .andExpect(jsonPath("$[?(@.name == 'Live')].type.key", contains("free")));

        mockMvc.perform(patch("/audios/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"album\":\"Nuevo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.album").value("Nuevo"))
            .andExpect(jsonPath("$.title").value("Corazón"))
            .andExpect(jsonPath("$.artist").value("José"))
            .andExpect(jsonPath("$.genre").value("Rock"))
            .andExpect(jsonPath("$.year").value(2010))
            .andExpect(jsonPath("$.tags", containsInAnyOrder("2010", "Live", "Nuevo", "Rock")));
        mockMvc.perform(patch("/audios/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"album\":null}"))
            .andExpect(jsonPath("$.album").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.tags", containsInAnyOrder("2010", "Live", "Rock")));
    }

    @Test
    void unifiedFilterMatchesEveryPrefixAndKeeps040Filters() throws Exception {
        int heart = upload("corazon.mp3", "Corazón", "José", "Álbum", "Rock", "2010");
        int heartless = upload("dos.mp3", "Heartless", "José", "Other", "Pop", "2012");
        int calle = upload("calle.mp3", "Calle", "jose", "Calle", "Rock", "2016");
        int sola = upload("sola.mp3", "Sola", "ana", "", "Jazz", "2009");
        int borde = upload("borde.mp3", "Borde", "Ana", "Álbum", "Rock", "2015");
        int anais = upload("anais.mp3", "Anais", "Anais", "Extra", "Metal", "2011");

        mockMvc.perform(post("/tag-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mood\"}"))
            .andExpect(status().isCreated());
        putTags(heart, "{\"tags\":[\"En Vivo\",{\"type\":\"idioma\",\"name\":\"Español\"},{\"type\":\"mood\",\"name\":\"gym\"}]}");
        putTags(heartless, "{\"tags\":[\"Live\"]}");
        putTags(borde, "{\"tags\":[{\"type\":\"language\",\"name\":\"Español\"}]}");

        playlist("Noche", heart, heartless);
        playlist("Día", calle);

        assertIds(List.of(heart, heartless, calle), get("/audios").param("f", "artista:jose"));
        assertIds(List.of(heart, heartless, calle), get("/audios").param("f", "artist:JOSÉ"));
        assertIds(List.of(sola, borde), get("/audios").param("f", "artista:ana"));
        assertIds(List.of(sola, borde, anais), get("/audios").param("artist", "ana"));

        assertIds(List.of(heart, calle, borde), get("/audios").param("f", "género:rock"));
        assertIds(List.of(heart, calle, borde), get("/audios").param("f", "genero:ROCK"));
        assertIds(List.of(heart, calle, borde), get("/audios").param("f", "genre:RÓCK"));
        assertIds(List.of(heart, borde), get("/audios").param("f", "álbum:álbum"));
        assertIds(List.of(heart, borde), get("/audios").param("f", "album:ALBUM"));
        assertIds(List.of(heartless), get("/audios").param("f", "album:other"));

        assertIds(List.of(heart), get("/audios").param("f", "año:2010"));
        assertIds(List.of(heart), get("/audios").param("f", "ano:2010"));
        assertIds(List.of(heart), get("/audios").param("f", "year:2010"));
        assertIds(List.of(heart, heartless, borde, anais), get("/audios").param("f", "year:2010-2015"));
        assertIds(List.of(heart, heartless, borde, anais), get("/audios").param("f", "año:2015-2010"));
        assertIds(List.of(), get("/audios").param("f", "year:2010").param("f", "year:2016"));

        assertIds(List.of(heart, borde), get("/audios").param("f", "idioma:espanol"));
        assertIds(List.of(heart, borde), get("/audios").param("f", "language:ESPAÑOL"));
        assertIds(List.of(heart, heartless), get("/audios").param("f", "playlist:noche"));
        assertIds(List.of(calle), get("/audios").param("f", "playlist:dia"));
        assertIds(List.of(calle), get("/audios").param("f", "playlist:DÍA"));
        assertIds(List.of(heart), get("/audios").param("f", "mood:GYM"));
        assertIds(List.of(heart), get("/audios").param("f", "Mood:gym"));
        assertIds(List.of(heart), get("/audios").param("f", "#en vivo"));
        assertIds(List.of(heart), get("/audios").param("f", "en vivo"));
        assertIds(List.of(heartless), get("/audios").param("f", "live"));
        assertIds(List.of(), get("/audios").param("f", "genre:rock").param("f", "genre:pop"));

        assertIds(List.of(heart), get("/audios")
            .param("f", "genre:rock")
            .param("f", "año:2010-2015")
            .param("f", "artista:jose"));
        assertIds(List.of(heart), get("/audios")
            .param("q", "corazon")
            .param("f", "genre:rock")
            .param("tag", "EN VIVO")
            .param("title", "cora"));
        assertIds(List.of(), get("/audios").param("tag", "espanol"));
        assertIds(List.of(heart, heartless, calle, sola, borde, anais), get("/audios").param("f", "   ").param("q", ""));

        List<Integer> rock = ids(get("/audios").param("f", "genre:rock"));
        List<Integer> shuffled = new ArrayList<>(rock);
        Collections.shuffle(shuffled, new Random(42L));
        assertEquals(shuffled, ids(get("/audios").param("f", "genre:rock").param("shuffle", "true").param("seed", "42")));
        assertEquals(shuffled, ids(get("/audios").param("f", "género:rock").param("shuffle", "TRUE").param("seed", "42")));

        mockMvc.perform(get("/audios").param("f", "nope:x"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mockMvc.perform(get("/audios").param("f", "year:nineties"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mockMvc.perform(get("/audios").param("f", "year:"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/audios").param("f", "#"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/filter/suggest").param("prefix", "género").param("q", "ro"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].type").value("genre"))
            .andExpect(jsonPath("$[0].value").value("Rock"))
            .andExpect(jsonPath("$[0].count").value(3));
        mockMvc.perform(get("/filter/suggest").param("prefix", "artist").param("q", "jos"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].type").value("artist"))
            .andExpect(jsonPath("$[0].value").value("José"))
            .andExpect(jsonPath("$[0].count").value(3));
        mockMvc.perform(get("/filter/suggest").param("prefix", "playlist").param("q", "di"))
            .andExpect(jsonPath("$[0].type").value("playlist"))
            .andExpect(jsonPath("$[0].value").value("Día"))
            .andExpect(jsonPath("$[0].count").value(1));
        mockMvc.perform(get("/filter/suggest").param("prefix", "mood").param("q", "g"))
            .andExpect(jsonPath("$[0].type").value("mood"))
            .andExpect(jsonPath("$[0].value").value("gym"))
            .andExpect(jsonPath("$[0].count").value(1));
        mockMvc.perform(get("/filter/suggest").param("prefix", "libre").param("q", "en"))
            .andExpect(jsonPath("$[0].type").value("free"))
            .andExpect(jsonPath("$[0].value").value("En Vivo"))
            .andExpect(jsonPath("$[0].count").value(1));
        mockMvc.perform(get("/filter/suggest").param("prefix", "year").param("q", "201"))
            .andExpect(jsonPath("$[*].value", contains("2010", "2011", "2012", "2015", "2016")));
        mockMvc.perform(get("/filter/suggest").param("prefix", "zz"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("artistTag", "no-esta").param("f", "genre:rock"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rebuildIsIdempotentAndDoesNotTouchTitleOrArtist() throws Exception {
        int kept = storedTrack(
            "Kept Title",
            "Kept Secondary",
            "Kept Artist",
            "Kept Album",
            null,
            null,
            Mp3Fixture.withTags("File Title", "File Artist", "File Album", "1", "Rock", "2011")
        );
        int filled = storedTrack(
            "Otro título",
            "Traducción",
            "Otro artista",
            null,
            null,
            null,
            Mp3Fixture.withTags("File Title", "File Artist", "Disco archivo", "2", "Jazz", "2001")
        );
        int missing = storedTrack("Huérfana", "Sigue", "Ana", "Disco", "Preset", 1990, null);

        mockMvc.perform(post("/admin/tags/rebuild-system"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processed").value(3))
            .andExpect(jsonPath("$.updated").value(2))
            .andExpect(jsonPath("$.unchanged").value(0))
            .andExpect(jsonPath("$.failed").value(1));

        AudioData keptAudio = audioDataRepository.findById(kept).orElseThrow();
        assertEquals("Kept Title", keptAudio.getTitle());
        assertEquals("Kept Secondary", keptAudio.getSecondaryTitle());
        assertEquals("Kept Artist", keptAudio.getArtist());
        assertEquals("Kept Album", keptAudio.getAlbum());
        assertEquals("Rock", keptAudio.getGenre());
        assertEquals(2011, keptAudio.getYear());
        assertFalse(artistRepository.existsByName("File Artist"));

        AudioData filledAudio = audioDataRepository.findById(filled).orElseThrow();
        assertEquals("Otro título", filledAudio.getTitle());
        assertEquals("Traducción", filledAudio.getSecondaryTitle());
        assertEquals("Otro artista", filledAudio.getArtist());
        assertEquals("Disco archivo", filledAudio.getAlbum());
        assertEquals("Jazz", filledAudio.getGenre());
        assertEquals(2001, filledAudio.getYear());

        AudioData missingAudio = audioDataRepository.findById(missing).orElseThrow();
        assertEquals("Huérfana", missingAudio.getTitle());
        assertEquals("Sigue", missingAudio.getSecondaryTitle());
        assertEquals("Ana", missingAudio.getArtist());
        assertEquals("Disco", missingAudio.getAlbum());
        assertEquals("Preset", missingAudio.getGenre());
        assertEquals(1990, missingAudio.getYear());

        mockMvc.perform(get("/audios"))
            .andExpect(jsonPath("$[?(@.title == 'Kept Title')].tags[*]", containsInAnyOrder("2011", "Kept Album", "Rock")))
            .andExpect(jsonPath("$[?(@.title == 'Otro título')].album", contains("Disco archivo")))
            .andExpect(jsonPath("$[?(@.title == 'Huérfana')].tags[*]").isEmpty());

        mockMvc.perform(post("/admin/tags/rebuild-system"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processed").value(3))
            .andExpect(jsonPath("$.updated").value(0))
            .andExpect(jsonPath("$.unchanged").value(2))
            .andExpect(jsonPath("$.failed").value(1));

        AudioData again = audioDataRepository.findById(kept).orElseThrow();
        assertEquals("Kept Title", again.getTitle());
        assertEquals("Kept Secondary", again.getSecondaryTitle());
        assertEquals("Kept Artist", again.getArtist());
        assertEquals("Kept Album", again.getAlbum());
        assertEquals("Rock", again.getGenre());
        assertEquals(2011, again.getYear());
    }

    private int upload(
            String filename,
            String title,
            String artist,
            String album,
            String genre,
            String year) throws Exception {
        byte[] bytes = Mp3Fixture.withTags(title, artist, album, "1", genre, year);
        mockMvc.perform(multipart("/audios")
                .file(new MockMultipartFile("file", filename, "audio/mpeg", bytes)))
            .andExpect(status().isCreated());
        return audioDataRepository.findAll().stream()
            .filter(audio -> title.equals(audio.getTitle()))
            .map(AudioData::getId)
            .findFirst()
            .orElseThrow();
    }

    private int storedTrack(
            String title,
            String secondary,
            String artist,
            String album,
            String genre,
            Integer year,
            byte[] bytes) throws Exception {
        String storageKey = UUID.randomUUID().toString();
        if (bytes != null) {
            audioFileService.store(new ByteArrayInputStream(bytes), storageKey, "mp3");
        }
        AudioData audio = new AudioData(title, storageKey, "mp3", audioFileService.databaseDirectory());
        audio.setTitle(title);
        audio.setSecondaryTitle(secondary);
        audio.setArtist(artist);
        audio.setAlbum(album);
        audio.setGenre(genre);
        audio.setYear(year);
        return audioDataRepository.save(audio).getId();
    }

    private void putTags(int audioId, String body) throws Exception {
        mockMvc.perform(put("/audios/" + audioId + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
    }

    private void playlist(String name, int... audioIds) throws Exception {
        String created = mockMvc.perform(post("/playlists")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        int id = JsonPath.read(created, "$.id");
        for (int audioId : audioIds) {
            mockMvc.perform(post("/playlists/" + id + "/items")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"audioId\":" + audioId + "}"))
                .andExpect(status().isCreated());
        }
    }

    private int typeId(String key) throws Exception {
        String body = mockMvc.perform(get("/tag-types"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        net.minidev.json.JSONArray ids = JsonPath.read(body, "$[?(@.key == '" + key + "')].id");
        return ((Number) ids.get(0)).intValue();
    }

    private String tagsOf(int audioId) throws Exception {
        return mockMvc.perform(get("/audios"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    private void assertIds(List<Integer> expected, MockHttpServletRequestBuilder request) throws Exception {
        assertEquals(expected, ids(request));
    }

    private List<Integer> ids(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        List<?> raw = JsonPath.read(body, "$[*].id");
        List<Integer> ids = new ArrayList<>();
        for (Object value : raw) {
            ids.add(((Number) value).intValue());
        }
        return ids;
    }
}
