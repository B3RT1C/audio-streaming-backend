package net.b3rt1c.music_streaming_backend;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.repository.ArtistRepository;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistItemRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistRepository;
import net.b3rt1c.music_streaming_backend.repository.TagRepository;

@SpringBootTest
@AutoConfigureMockMvc
class LibraryFilterApiTest {

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
    private JdbcTemplate jdbc;

    private int heart;
    private int heartless;
    private int otra;
    private int calle;
    private int solo;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM play_events");
        jdbc.update("DELETE FROM audio_tags");
        jdbc.update("DELETE FROM artist_tags");
        jdbc.update("DELETE FROM playlist_tags");
        playlistItemRepository.deleteAll();
        playlistRepository.deleteAll();
        audioDataRepository.deleteAll();
        artistRepository.deleteAll();
        tagRepository.deleteAll();
    }

    @Test
    void titleFilterMatchesTitleOrSecondaryTitleIgnoringCaseAndAccents() throws Exception {
        library();

        mockMvc.perform(get("/audios").param("title", "CORAZÓN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", contains(heart, otra)));
        mockMvc.perform(get("/audios").param("title", "heart"))
            .andExpect(jsonPath("$[*].id", contains(heart, heartless)));
        mockMvc.perform(get("/audios").param("title", "traduccion"))
            .andExpect(jsonPath("$[*].id", contains(solo)));

        mockMvc.perform(get("/audios").param("title", "tema"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("title", "álbum"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("title", "corazon.mp3"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("title", "jose"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("title", "no-esta"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("title", "   "))
            .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void artistFilterMatchesOnlyTheTrackArtistText() throws Exception {
        library();

        mockMvc.perform(get("/audios").param("artist", "JOSE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", contains(heart, heartless, calle)));
        mockMvc.perform(get("/audios").param("artist", "ana"))
            .andExpect(jsonPath("$[*].id", contains(otra)));
        mockMvc.perform(get("/audios").param("artist", "álbum"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("artist", "corazon"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("artist", "nadie"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("artist", " "))
            .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void repeatedTrackTagsAreAndAndASingleTagStaysCompatible() throws Exception {
        library();

        mockMvc.perform(get("/audios").param("tag", "ROCK"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", contains(heart, heartless)));
        mockMvc.perform(get("/audios").param("tag", "en vivo"))
            .andExpect(jsonPath("$[*].id", contains(heart)));
        mockMvc.perform(get("/audios").param("tag", "Rock", "En Vivo"))
            .andExpect(jsonPath("$[*].id", contains(heart)));
        mockMvc.perform(get("/audios").param("tag", "Rock", "   "))
            .andExpect(jsonPath("$[*].id", contains(heart, heartless)));
        mockMvc.perform(get("/audios").param("tag", "Rock", "Live"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("tag", "vivo"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("tag", "espanol"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("tag", "ESPAÑOL"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("tag", "no-esta"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("tag", "   "))
            .andExpect(jsonPath("$", hasSize(5)));

        mockMvc.perform(get("/audios").param("tag", "rock").param("q", "other"))
            .andExpect(jsonPath("$[*].id", contains(heartless)));
        mockMvc.perform(get("/audios").param("tag", "rock").param("q", "traduccion"))
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void artistTagsRequireEveryTagOnTheExactArtistRow() throws Exception {
        library();

        mockMvc.perform(get("/audios").param("artistTag", "español"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", contains(heart, heartless)));
        mockMvc.perform(get("/audios").param("artistTag", "LIVE"))
            .andExpect(jsonPath("$[*].id", contains(heart, heartless, calle)));
        mockMvc.perform(get("/audios").param("artistTag", "Español", "Live"))
            .andExpect(jsonPath("$[*].id", contains(heart, heartless)));
        mockMvc.perform(get("/audios").param("artistTag", "Live", "Pop"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("artistTag", "espanol"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("artistTag", "no-esta"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("artist", "jose").param("artistTag", "Live"))
            .andExpect(jsonPath("$[*].id", contains(heart, heartless, calle)));
        mockMvc.perform(get("/audios").param("artist", "jose").param("artistTag", "Español"))
            .andExpect(jsonPath("$[*].id", contains(heart, heartless)));
    }

    @Test
    void playlistTagsRequireOnePlaylistToHoldEveryTag() throws Exception {
        library();

        mockMvc.perform(get("/audios").param("playlistTag", "NOCHE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", contains(heart, otra, calle)));
        mockMvc.perform(get("/audios").param("playlistTag", "Favorita"))
            .andExpect(jsonPath("$[*].id", contains(heart, heartless, otra)));
        mockMvc.perform(get("/audios").param("playlistTag", "Noche", "Favorita"))
            .andExpect(jsonPath("$[*].id", contains(heart, otra)));
        mockMvc.perform(get("/audios").param("playlistTag", "Noche", "Dia"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("playlistTag", "fav"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("playlistTag", "no-esta"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("playlistTag", " ", "Favorita"))
            .andExpect(jsonPath("$[*].id", contains(heart, heartless, otra)));
    }

    @Test
    void filtersCombineWithAndAndBlankParametersAreIgnored() throws Exception {
        library();

        mockMvc.perform(get("/audios")
                .param("q", "album")
                .param("title", "corazon")
                .param("artist", "jose")
                .param("tag", "Rock", "En Vivo")
                .param("artistTag", "Español")
                .param("playlistTag", "Noche", "Favorita"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", contains(heart)));

        mockMvc.perform(get("/audios")
                .param("q", "album")
                .param("title", "corazon")
                .param("artist", "ana")
                .param("tag", "Live")
                .param("playlistTag", "Favorita"))
            .andExpect(jsonPath("$[*].id", contains(otra)));

        mockMvc.perform(get("/audios")
                .param("title", "heart")
                .param("tag", "En Vivo")
                .param("artistTag", "Español"))
            .andExpect(jsonPath("$[*].id", contains(heart)));

        mockMvc.perform(get("/audios")
                .param("q", "")
                .param("title", " ")
                .param("artist", "")
                .param("tag", "")
                .param("artistTag", "  ")
                .param("playlistTag", "")
                .param("shuffle", " ")
                .param("seed", " "))
            .andExpect(jsonPath("$[*].id", contains(heart, heartless, otra, calle, solo)));
    }

    @Test
    void legacyQueryStillMatchesTheSameFieldsAndKeepsIdOrder() throws Exception {
        library();

        mockMvc.perform(get("/audios").param("q", "corazon"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", contains(heart, otra)));
        mockMvc.perform(get("/audios").param("q", "ÁLBUM"))
            .andExpect(jsonPath("$[*].id", contains(heart, otra)));
        mockMvc.perform(get("/audios").param("q", "tema"))
            .andExpect(jsonPath("$[*].id", contains(heart)));
        mockMvc.perform(get("/audios").param("q", "dos.mp3"))
            .andExpect(jsonPath("$[*].id", contains(heartless)));
        mockMvc.perform(get("/audios").param("q", "TRADUCCION"))
            .andExpect(jsonPath("$[*].id", contains(solo)));
        mockMvc.perform(get("/audios").param("q", "no-esta"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios"))
            .andExpect(jsonPath("$[*].id", contains(heart, heartless, otra, calle, solo)));
    }

    @Test
    void shuffleIsDeterministicPerSeedAndAppliesAfterFilters() throws Exception {
        int first = saveTrack("A", "Alfa", null, "Uno", null, "a.mp3");
        int second = saveTrack("B", "Beta", null, "Dos", null, "b.mp3");
        int third = saveTrack("C", "Gamma", null, "Tres", null, "c.mp3");
        int fourth = saveTrack("D", "Delta", null, "Cuatro", null, "d.mp3");
        int fifth = saveTrack("E", "Epsilon", null, "Cinco", null, "e.mp3");
        int sixth = saveTrack("F", "Zeta", null, "Seis", null, "f.mp3");
        List<Integer> ordered = List.of(first, second, third, fourth, fifth, sixth);

        assertEquals(ordered, ids(get("/audios")));
        assertEquals(ordered, ids(get("/audios").param("shuffle", "false").param("seed", "42")));
        assertEquals(ordered, ids(get("/audios").param("seed", "no-es-entero")));

        List<Integer> seed42 = new ArrayList<>(ordered);
        Collections.shuffle(seed42, new Random(42L));
        List<Integer> seed7 = new ArrayList<>(ordered);
        Collections.shuffle(seed7, new Random(7L));
        assertNotEquals(seed42, seed7);

        assertEquals(seed42, ids(get("/audios").param("shuffle", "true").param("seed", "42")));
        assertEquals(seed42, ids(get("/audios").param("shuffle", "TRUE").param("seed", "42")));
        assertEquals(seed7, ids(get("/audios").param("shuffle", "true").param("seed", "7")));

        List<Integer> negative = new ArrayList<>(ordered);
        Collections.shuffle(negative, new Random(-1L));
        assertEquals(negative, ids(get("/audios").param("shuffle", "true").param("seed", "-1")));

        mockMvc.perform(get("/audios").param("shuffle", "true").param("tag", "no-esta").param("seed", "42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());

        List<Integer> filtered = ids(get("/audios").param("title", "a"));
        List<Integer> filteredShuffle = new ArrayList<>(filtered);
        Collections.shuffle(filteredShuffle, new Random(99L));
        assertEquals(
            filteredShuffle,
            ids(get("/audios").param("title", "a").param("shuffle", "true").param("seed", "99"))
        );

        List<Integer> unseeded = ids(get("/audios").param("shuffle", "true").param("seed", "  "));
        assertEquals(ordered.size(), unseeded.size());
        mockMvc.perform(get("/audios").param("shuffle", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", containsInAnyOrder(ordered.toArray())));

        mockMvc.perform(get("/audios").param("shuffle", "true").param("seed", "no"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mockMvc.perform(get("/audios").param("shuffle", "true").param("seed", "1.5"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mockMvc.perform(get("/audios").param("shuffle", "yes"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
    }

    @Test
    void playlistsFilterByFoldedNameAndRequireEveryTag() throws Exception {
        library();

        mockMvc.perform(get("/playlists"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name", contains("Noche", "Día", "Mi Noche")))
            .andExpect(jsonPath("$[0].items").doesNotExist());

        mockMvc.perform(get("/playlists").param("q", "NOCHE"))
            .andExpect(jsonPath("$[*].name", contains("Noche", "Mi Noche")));
        mockMvc.perform(get("/playlists").param("q", "dia"))
            .andExpect(jsonPath("$[*].name", contains("Día")));
        mockMvc.perform(get("/playlists").param("q", "no-esta"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/playlists").param("q", " "))
            .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(get("/playlists").param("tag", "favorita"))
            .andExpect(jsonPath("$[*].name", contains("Noche", "Día")));
        mockMvc.perform(get("/playlists").param("tag", "Noche", "Favorita"))
            .andExpect(jsonPath("$[*].name", contains("Noche")));
        mockMvc.perform(get("/playlists").param("tag", "Noche", "Dia"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/playlists").param("tag", "noche").param("q", "mi"))
            .andExpect(jsonPath("$[*].name", contains("Mi Noche")));
        mockMvc.perform(get("/playlists").param("tag", "no-esta"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/playlists").param("tag", " ").param("q", ""))
            .andExpect(jsonPath("$[*].name", contains("Noche", "Día", "Mi Noche")));
    }

    @Test
    void artistsFilterByFoldedNameAndRequireEveryTag() throws Exception {
        library();

        mockMvc.perform(get("/artists"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name", contains("ana", "jose", "José")));

        mockMvc.perform(get("/artists").param("q", "JOSE"))
            .andExpect(jsonPath("$[*].name", contains("jose", "José")))
            .andExpect(jsonPath("$[0].audioCount").value(1))
            .andExpect(jsonPath("$[1].audioCount").value(2));
        mockMvc.perform(get("/artists").param("q", "ana"))
            .andExpect(jsonPath("$[*].name", contains("ana")));
        mockMvc.perform(get("/artists").param("q", "no-esta"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/artists").param("tag", "LIVE"))
            .andExpect(jsonPath("$[*].name", contains("jose", "José")));
        mockMvc.perform(get("/artists").param("tag", "Español", "Live"))
            .andExpect(jsonPath("$[*].name", contains("José")))
            .andExpect(jsonPath("$[0].audioCount").value(2));
        mockMvc.perform(get("/artists").param("tag", "espanol"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/artists").param("tag", "Live", "Pop"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/artists").param("q", "jose").param("tag", "Español"))
            .andExpect(jsonPath("$[*].name", contains("José")));
        mockMvc.perform(get("/artists").param("tag", " ").param("q", " "))
            .andExpect(jsonPath("$[*].name", contains("ana", "jose", "José")));
    }

    private void library() throws Exception {
        heart = saveTrack("Tema", "Corazón", "Heart", "José", "Álbum", "corazon.mp3");
        heartless = saveTrack("Dos", "Heartless", null, "José", "Other", "dos.mp3");
        otra = saveTrack("Tres", "Otra", "Corazon", "ana", "Álbum", "tres.mp3");
        calle = saveTrack("Cuatro", "Calle", null, "jose", "Calle", "cuatro.mp3");
        solo = saveTrack("Cinco", "Solo", "Traducción", null, null, "cinco.mp3");

        patchArtist(heart, "José");
        patchArtist(heartless, "José");
        patchArtist(otra, "ana");
        patchArtist(calle, "jose");

        putTags("/audios/" + heart + "/tags", "Rock", "En Vivo");
        putTags("/audios/" + heartless + "/tags", "Rock");
        putTags("/audios/" + otra + "/tags", "Live");

        putTags("/artists/" + artistId("José") + "/tags", "Español", "Live");
        putTags("/artists/" + artistId("jose") + "/tags", "Live");
        putTags("/artists/" + artistId("ana") + "/tags", "Pop");

        playlist("Noche", new String[] {"Noche", "Favorita"}, heart, otra);
        playlist("Día", new String[] {"Dia", "Favorita"}, heartless);
        playlist("Mi Noche", new String[] {"Noche"}, calle);
    }

    private int saveTrack(
            String name,
            String title,
            String secondary,
            String artist,
            String album,
            String filename) {
        AudioData audio = new AudioData(name, UUID.randomUUID().toString(), "mp3", "uploads/songs/");
        audio.setTitle(title);
        audio.setSecondaryTitle(secondary);
        audio.setArtist(artist);
        audio.setAlbum(album);
        audio.setOriginalFilename(filename);
        audio.setCreatedAt(Instant.now());
        return audioDataRepository.save(audio).getId();
    }

    private void patchArtist(int audioId, String artist) throws Exception {
        mockMvc.perform(patch("/audios/" + audioId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"artist\":\"" + artist + "\"}"))
            .andExpect(status().isOk());
    }

    private void putTags(String path, String... tags) throws Exception {
        mockMvc.perform(put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tagsJson(tags)))
            .andExpect(status().isOk());
    }

    private int playlist(String name, String[] tags, int... audioIds) throws Exception {
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
        if (tags.length > 0) {
            putTags("/playlists/" + id + "/tags", tags);
        }
        return id;
    }

    private int artistId(String name) throws Exception {
        String body = mockMvc.perform(get("/artists"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        net.minidev.json.JSONArray ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ((Number) ids.get(0)).intValue();
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

    private static String tagsJson(String... tags) {
        StringBuilder body = new StringBuilder("{\"tags\":[");
        for (int i = 0; i < tags.length; i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append('"').append(tags[i]).append('"');
        }
        body.append("]}");
        return body.toString();
    }
}
