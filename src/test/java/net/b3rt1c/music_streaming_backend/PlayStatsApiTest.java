package net.b3rt1c.music_streaming_backend;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import net.b3rt1c.music_streaming_backend.model.Artist;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.repository.ArtistRepository;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistRepository;
import net.b3rt1c.music_streaming_backend.repository.TagRepository;
import net.b3rt1c.music_streaming_backend.service.SystemTagService;

@SpringBootTest
@AutoConfigureMockMvc
class PlayStatsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AudioDataRepository audioDataRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private SystemTagService systemTagService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM play_events");
        jdbc.update("DELETE FROM audio_tags");
        jdbc.update("DELETE FROM artist_tags");
        jdbc.update("DELETE FROM playlist_tags");
        playlistRepository.deleteAll();
        audioDataRepository.deleteAll();
        artistRepository.deleteAll();
        tagRepository.deleteAll();
    }

    @Test
    void emptyStatsAndInvalidQueries() throws Exception {
        mockMvc.perform(get("/stats/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plays").value(0))
            .andExpect(jsonPath("$.listenedSeconds").value(0))
            .andExpect(jsonPath("$.distinctAudios").value(0))
            .andExpect(jsonPath("$.distinctArtists").value(0))
            .andExpect(jsonPath("$.from").value(nullValue()))
            .andExpect(jsonPath("$.to").value(nullValue()));
        mockMvc.perform(get("/stats/timeline").param("granularity", "day"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/stats/top"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mockMvc.perform(get("/stats/top").param("kind", "album"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mockMvc.perform(get("/stats/timeline"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mockMvc.perform(get("/stats/timeline").param("granularity", "week"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mockMvc.perform(get("/stats/summary").param("from", "2026-13-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mockMvc.perform(get("/stats/summary").param("from", "2026-02-01").param("to", "2026-01-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mockMvc.perform(get("/stats/top").param("kind", "audio").param("limit", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
    }

    @Test
    void recordsAPlayAndRejectsInvalidBodies() throws Exception {
        int audioId = saveTrack("Tema", "Ana", null, null, null, null);
        Instant before = Instant.now();

        String created = play(body(audioId, null, 40, null, "Playlist", null, null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.audioId").value(audioId))
            .andExpect(jsonPath("$.listenedSeconds").value(40))
            .andExpect(jsonPath("$.completed").value(false))
            .andExpect(jsonPath("$.counted").value(true))
            .andExpect(jsonPath("$.source").value("playlist"))
            .andExpect(jsonPath("$.playlistId").value(nullValue()))
            .andExpect(jsonPath("$.clientEventId").value(nullValue()))
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn().getResponse().getContentAsString();
        Instant startedAt = Instant.parse(JsonPath.read(created, "$.startedAt"));
        assertEquals(false, startedAt.isBefore(before.minusSeconds(5)));

        play("{\"listenedSeconds\":10}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_BODY"));
        play("{\"audioId\":" + audioId + "}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_BODY"));
        play(body(audioId, null, -1, null, null, null, null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_BODY"));
        play(body(999999, null, 10, null, null, null, null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_BODY"))
            .andExpect(jsonPath("$.message").value("Audio not found"));
        play(body(audioId, null, 10, null, "radio", null, null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_BODY"));
        play(body(audioId, null, 10, null, null, 999999, null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Playlist not found"));
        play("{\"audioId\":" + audioId + ",\"listenedSeconds\":\"no\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_BODY"));
    }

    @Test
    void countedFollowsTheDurationRuleAndTrimsToDurationPlusSlack() throws Exception {
        int unknown = saveTrack("Sin duración", null, null, null, null, null);
        int shortTrack = saveTrack("Corta", null, 40, null, null, null);
        int longTrack = saveTrack("Larga", null, 200, null, null, null);
        int tiny = saveTrack("Muy corta", null, 10, null, null, null);
        int odd = saveTrack("Impar", null, 31, null, null, null);
        int empty = saveTrack("Vacía", null, 0, null, null, null);

        play(body(unknown, null, 29, null, null, null, null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.listenedSeconds").value(29))
            .andExpect(jsonPath("$.counted").value(false));
        play(body(unknown, null, 30, null, null, null, null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.counted").value(true));

        play(body(shortTrack, null, 19, null, null, null, null))
            .andExpect(jsonPath("$.listenedSeconds").value(19))
            .andExpect(jsonPath("$.counted").value(false));
        play(body(shortTrack, null, 20, true, null, null, null))
            .andExpect(jsonPath("$.listenedSeconds").value(20))
            .andExpect(jsonPath("$.completed").value(true))
            .andExpect(jsonPath("$.counted").value(true));

        play(body(longTrack, null, 29, null, null, null, null))
            .andExpect(jsonPath("$.counted").value(false));
        play(body(longTrack, null, 500, null, null, null, null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.listenedSeconds").value(205))
            .andExpect(jsonPath("$.counted").value(true));

        play(body(tiny, null, 100, null, null, null, null))
            .andExpect(jsonPath("$.listenedSeconds").value(15))
            .andExpect(jsonPath("$.counted").value(true));
        play(body(tiny, null, 3, null, null, null, null))
            .andExpect(jsonPath("$.listenedSeconds").value(3))
            .andExpect(jsonPath("$.counted").value(false));

        play(body(odd, null, 15, null, null, null, null))
            .andExpect(jsonPath("$.counted").value(false));
        play(body(odd, null, 16, null, null, null, null))
            .andExpect(jsonPath("$.counted").value(true));

        play(body(empty, null, 0, null, null, null, null))
            .andExpect(jsonPath("$.listenedSeconds").value(0))
            .andExpect(jsonPath("$.counted").value(true));

        mockMvc.perform(get("/stats/summary"))
            .andExpect(jsonPath("$.plays").value(6))
            .andExpect(jsonPath("$.listenedSeconds").value(29 + 30 + 19 + 20 + 29 + 205 + 15 + 3 + 15 + 16 + 0))
            .andExpect(jsonPath("$.distinctAudios").value(6));
    }

    @Test
    void clientEventIdIsIdempotentAndBlankIdsDoNotCollide() throws Exception {
        int audioId = saveTrack("Tema", "Ana", 200, null, null, null);
        int otherId = saveTrack("Otra", "Ana", 200, null, null, null);
        String clientEventId = "  evt-1  ";

        String created = play(body(audioId, "2026-03-01T12:00:00Z", 40, false, "library", null, clientEventId))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clientEventId").value("evt-1"))
            .andExpect(jsonPath("$.listenedSeconds").value(40))
            .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");

        play(body(otherId, "2026-04-01T12:00:00Z", 80, true, "search", null, "evt-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.audioId").value(audioId))
            .andExpect(jsonPath("$.listenedSeconds").value(40))
            .andExpect(jsonPath("$.source").value("library"))
            .andExpect(jsonPath("$.completed").value(false));

        play(body(audioId, null, 40, null, null, null, "   "))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clientEventId").value(nullValue()));
        play(body(audioId, null, 40, null, null, null, ""))
            .andExpect(status().isCreated());

        String longId = "c".repeat(300);
        play(body(audioId, null, 40, null, null, null, longId))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clientEventId").value(longId));
        play(body(audioId, null, 5, null, null, null, longId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.listenedSeconds").value(40));

        mockMvc.perform(get("/stats/summary"))
            .andExpect(jsonPath("$.plays").value(4));
    }

    @Test
    void deleteRemovesOneEvent() throws Exception {
        int audioId = saveTrack("Tema", "Ana", 200, null, null, null);
        String created = play(body(audioId, "2026-03-01T12:00:00Z", 40, null, null, null, "gone"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");
        play(body(audioId, "2026-03-02T12:00:00Z", 10, null, null, null, null))
            .andExpect(status().isCreated());

        mockMvc.perform(delete("/plays/" + id))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/plays/" + id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        play(body(audioId, null, 80, null, null, null, "gone"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.listenedSeconds").value(80));

        mockMvc.perform(get("/stats/summary"))
            .andExpect(jsonPath("$.plays").value(1))
            .andExpect(jsonPath("$.listenedSeconds").value(90));
    }

    @Test
    void rankingsUseCountedPlaysAndCurrentCatalogNames() throws Exception {
        artist("Ana");
        artist("Bea");
        int alta = saveTrack("Alta", "Ana", 200, "Rock", 2010, "Disco");
        int media = saveTrack("Media", "Ana", 200, "Pop", null, null);
        int baja = saveTrack("Baja", "Bea", 200, null, null, null);
        int eco = saveTrack("Eco", "Bea", 200, null, null, null);
        int corta = saveTrack("Corta", null, 200, null, null, null);
        int salto = saveTrack("Salto", "ana", 200, null, null, null);
        mockMvc.perform(put("/audios/" + alta + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\"Live\"]}"))
            .andExpect(status().isOk());
        int playlistId = playlist("Noche");

        play(body(alta, "2026-06-01T10:00:00Z", 40, null, "playlist", playlistId, null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.playlistId").value(playlistId))
            .andExpect(jsonPath("$.counted").value(true));
        play(body(alta, "2026-06-02T10:00:00Z", 40, null, "library", null, null))
            .andExpect(jsonPath("$.counted").value(true));
        play(body(alta, "2026-06-02T11:00:00Z", 10, null, "library", null, null))
            .andExpect(jsonPath("$.counted").value(false));
        play(body(media, "2026-06-03T10:00:00Z", 50, null, null, null, null))
            .andExpect(jsonPath("$.counted").value(true));
        play(body(baja, "2026-06-04T10:00:00Z", 40, null, null, null, null)).andExpect(status().isCreated());
        play(body(eco, "2026-06-04T12:00:00Z", 40, null, null, null, null)).andExpect(status().isCreated());
        play(body(corta, "2026-06-05T10:00:00Z", 30, null, null, null, null)).andExpect(status().isCreated());
        play(body(salto, "2026-06-05T12:00:00Z", 40, null, null, null, null)).andExpect(status().isCreated());

        mockMvc.perform(get("/stats/summary"))
            .andExpect(jsonPath("$.plays").value(7))
            .andExpect(jsonPath("$.listenedSeconds").value(290))
            .andExpect(jsonPath("$.distinctAudios").value(6))
            .andExpect(jsonPath("$.distinctArtists").value(2))
            .andExpect(jsonPath("$.from").value(nullValue()));

        mockMvc.perform(get("/stats/summary").param("from", "2026-06-01").param("to", "2026-06-01"))
            .andExpect(jsonPath("$.plays").value(1))
            .andExpect(jsonPath("$.listenedSeconds").value(40))
            .andExpect(jsonPath("$.distinctAudios").value(1))
            .andExpect(jsonPath("$.distinctArtists").value(1))
            .andExpect(jsonPath("$.from").value("2026-06-01"))
            .andExpect(jsonPath("$.to").value("2026-06-01"));

        mockMvc.perform(get("/stats/top").param("kind", "audio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(6)))
            .andExpect(jsonPath("$[0].name").value("Alta"))
            .andExpect(jsonPath("$[0].secondaryName").value("Ana"))
            .andExpect(jsonPath("$[0].plays").value(2))
            .andExpect(jsonPath("$[0].listenedSeconds").value(90))
            .andExpect(jsonPath("$[1].name").value("Media"))
            .andExpect(jsonPath("$[1].plays").value(1))
            .andExpect(jsonPath("$[1].listenedSeconds").value(50))
            .andExpect(jsonPath("$[*].name", contains("Alta", "Media", "Baja", "Eco", "Salto", "Corta")));

        mockMvc.perform(get("/stats/top").param("kind", "audio").param("limit", "1"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("Alta"));

        mockMvc.perform(get("/stats/top").param("kind", "Audio"))
            .andExpect(jsonPath("$[0].name").value("Alta"));

        mockMvc.perform(get("/stats/top").param("kind", "audio"))
            .andExpect(jsonPath("$[5].name").value("Corta"))
            .andExpect(jsonPath("$[5].id").value(corta))
            .andExpect(jsonPath("$[5].secondaryName").doesNotExist());

        mockMvc.perform(get("/stats/top").param("kind", "artist"))
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].name").value("Ana"))
            .andExpect(jsonPath("$[0].plays").value(3))
            .andExpect(jsonPath("$[0].listenedSeconds").value(140))
            .andExpect(jsonPath("$[0].secondaryName").doesNotExist())
            .andExpect(jsonPath("$[1].name").value("Bea"))
            .andExpect(jsonPath("$[1].plays").value(2))
            .andExpect(jsonPath("$[1].listenedSeconds").value(80));

        mockMvc.perform(get("/stats/top").param("kind", "tag"))
            .andExpect(jsonPath("$[*].name", contains("Rock", "Disco", "2010", "Live", "Pop")))
            .andExpect(jsonPath("$[0].secondaryName").value("Género"))
            .andExpect(jsonPath("$[0].plays").value(2))
            .andExpect(jsonPath("$[0].listenedSeconds").value(90))
            .andExpect(jsonPath("$[1].secondaryName").value("Álbum"))
            .andExpect(jsonPath("$[2].name").value("2010"))
            .andExpect(jsonPath("$[2].secondaryName").value("Año"))
            .andExpect(jsonPath("$[3].name").value("Live"))
            .andExpect(jsonPath("$[3].secondaryName").value("Libre"))
            .andExpect(jsonPath("$[4].name").value("Pop"))
            .andExpect(jsonPath("$[4].plays").value(1))
            .andExpect(jsonPath("$[4].listenedSeconds").value(50));

        mockMvc.perform(get("/stats/top").param("kind", "tag").param("tagType", "genre"))
            .andExpect(jsonPath("$[*].name", contains("Rock", "Pop")));
        mockMvc.perform(get("/stats/top").param("kind", "tag").param("tagType", "year"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("2010"))
            .andExpect(jsonPath("$[0].secondaryName").value("Año"));
        mockMvc.perform(get("/stats/top").param("kind", "tag").param("tagType", "Género"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/stats/top").param("kind", "tag").param("tagType", "missing"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/stats/top").param("kind", "audio").param("tagType", "genre"))
            .andExpect(jsonPath("$[0].name").value("Alta"));

        mockMvc.perform(get("/stats/top").param("kind", "playlist"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(playlistId))
            .andExpect(jsonPath("$[0].name").value("Noche"))
            .andExpect(jsonPath("$[0].secondaryName").doesNotExist())
            .andExpect(jsonPath("$[0].plays").value(1))
            .andExpect(jsonPath("$[0].listenedSeconds").value(40));
    }

    @Test
    void deletingAPlaylistKeepsTheListenAndDeletingTheAudioDropsIt() throws Exception {
        int audioId = saveTrack("Tema", "Ana", 200, null, null, null);
        int playlistId = playlist("Noche");
        play(body(audioId, "2026-06-01T10:00:00Z", 40, null, "playlist", playlistId, null))
            .andExpect(status().isCreated());

        mockMvc.perform(delete("/playlists/" + playlistId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/stats/top").param("kind", "playlist"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/stats/summary"))
            .andExpect(jsonPath("$.plays").value(1))
            .andExpect(jsonPath("$.listenedSeconds").value(40));

        mockMvc.perform(delete("/audios/" + audioId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/stats/summary"))
            .andExpect(jsonPath("$.plays").value(0))
            .andExpect(jsonPath("$.listenedSeconds").value(0));
        mockMvc.perform(get("/stats/top").param("kind", "audio"))
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void tagsAddedLaterStillReceiveEarlierPlays() throws Exception {
        int audioId = saveTrack("Tema", "Ana", 200, null, null, null);
        play(body(audioId, "2026-06-01T10:00:00Z", 40, null, null, null, null))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/stats/top").param("kind", "tag"))
            .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(put("/audios/" + audioId + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\"Live\"]}"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/stats/top").param("kind", "tag").param("tagType", "free"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("Live"))
            .andExpect(jsonPath("$[0].plays").value(1))
            .andExpect(jsonPath("$[0].listenedSeconds").value(40));
    }

    @Test
    void timelineUsesMadridAndFillsGaps() throws Exception {
        int audioId = saveTrack("Tema", "Ana", 200, null, null, null);
        play(body(audioId, "2025-12-31T23:30:00Z", 30, null, null, null, null))
            .andExpect(jsonPath("$.counted").value(true));
        play(body(audioId, "2026-01-15T22:30:00Z", 40, null, null, null, null))
            .andExpect(jsonPath("$.counted").value(true));
        play(body(audioId, "2026-01-15T23:30:00Z", 40, null, null, null, null))
            .andExpect(jsonPath("$.counted").value(true));
        play(body(audioId, "2026-01-18T10:00:00Z", 10, null, null, null, null))
            .andExpect(jsonPath("$.counted").value(false));

        mockMvc.perform(get("/stats/summary").param("from", "2026-01-15").param("to", "2026-01-15"))
            .andExpect(jsonPath("$.plays").value(1))
            .andExpect(jsonPath("$.listenedSeconds").value(40));
        mockMvc.perform(get("/stats/summary").param("from", "2026-01-16").param("to", "2026-01-16"))
            .andExpect(jsonPath("$.plays").value(1))
            .andExpect(jsonPath("$.listenedSeconds").value(40));

        mockMvc.perform(get("/stats/timeline").param("granularity", "day")
                .param("from", "2026-01-15").param("to", "2026-01-18"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(4)))
            .andExpect(jsonPath("$[0].period").value("2026-01-15"))
            .andExpect(jsonPath("$[0].plays").value(1))
            .andExpect(jsonPath("$[0].listenedSeconds").value(40))
            .andExpect(jsonPath("$[1].period").value("2026-01-16"))
            .andExpect(jsonPath("$[1].plays").value(1))
            .andExpect(jsonPath("$[1].listenedSeconds").value(40))
            .andExpect(jsonPath("$[2].period").value("2026-01-17"))
            .andExpect(jsonPath("$[2].plays").value(0))
            .andExpect(jsonPath("$[2].listenedSeconds").value(0))
            .andExpect(jsonPath("$[3].period").value("2026-01-18"))
            .andExpect(jsonPath("$[3].plays").value(0))
            .andExpect(jsonPath("$[3].listenedSeconds").value(10));

        mockMvc.perform(get("/stats/timeline").param("granularity", "day").param("from", "2026-01-16"))
            .andExpect(jsonPath("$[*].period", contains("2026-01-16", "2026-01-17", "2026-01-18")));

        mockMvc.perform(get("/stats/timeline").param("granularity", "month")
                .param("from", "2025-12-01").param("to", "2026-01-31"))
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].period").value("2025-12"))
            .andExpect(jsonPath("$[0].plays").value(0))
            .andExpect(jsonPath("$[0].listenedSeconds").value(0))
            .andExpect(jsonPath("$[1].period").value("2026-01"))
            .andExpect(jsonPath("$[1].plays").value(3))
            .andExpect(jsonPath("$[1].listenedSeconds").value(120));

        mockMvc.perform(get("/stats/timeline").param("granularity", "year")
                .param("from", "2025-06-01").param("to", "2026-06-01"))
            .andExpect(jsonPath("$[*].period", contains("2025", "2026")))
            .andExpect(jsonPath("$[0].plays").value(0))
            .andExpect(jsonPath("$[1].plays").value(3))
            .andExpect(jsonPath("$[1].listenedSeconds").value(120));

        mockMvc.perform(get("/stats/timeline").param("granularity", "day")
                .param("from", "2024-05-01").param("to", "2024-05-02"))
            .andExpect(jsonPath("$[*].period", contains("2024-05-01", "2024-05-02")))
            .andExpect(jsonPath("$[0].plays").value(0))
            .andExpect(jsonPath("$[1].plays").value(0));

        mockMvc.perform(get("/stats/timeline").param("granularity", "DAY"))
            .andExpect(jsonPath("$", hasSize(18)))
            .andExpect(jsonPath("$[0].period").value("2026-01-01"))
            .andExpect(jsonPath("$[0].plays").value(1))
            .andExpect(jsonPath("$[17].period").value("2026-01-18"));
    }

    private ResultActions play(String json) throws Exception {
        return mockMvc.perform(post("/plays")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
    }

    private int playlist(String name) throws Exception {
        String created = mockMvc.perform(post("/playlists")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    private int saveTrack(
            String title,
            String artist,
            Integer duration,
            String genre,
            Integer year,
            String album) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            AudioData audio = new AudioData(title, UUID.randomUUID().toString(), "mp3", "uploads/songs/");
            audio.setTitle(title);
            audio.setArtist(artist);
            audio.setAlbum(album);
            audio.setGenre(genre);
            audio.setYear(year);
            audio.setDurationSeconds(duration);
            audio.setOriginalFilename(title + ".mp3");
            audio.setCreatedAt(Instant.now());
            audioDataRepository.save(audio);
            systemTagService.syncDerived(audio);
            return audio.getId();
        });
    }

    private void artist(String name) {
        if (artistRepository.existsByName(name)) {
            return;
        }
        Artist row = new Artist();
        row.setName(name);
        artistRepository.save(row);
    }

    private static String body(
            int audioId,
            String startedAt,
            int seconds,
            Boolean completed,
            String source,
            Integer playlistId,
            String clientEventId) {
        StringBuilder json = new StringBuilder();
        json.append("{\"audioId\":").append(audioId);
        json.append(",\"listenedSeconds\":").append(seconds);
        if (startedAt != null) {
            json.append(",\"startedAt\":\"").append(startedAt).append('"');
        }
        if (completed != null) {
            json.append(",\"completed\":").append(completed);
        }
        if (source != null) {
            json.append(",\"source\":\"").append(source).append('"');
        }
        if (playlistId != null) {
            json.append(",\"playlistId\":").append(playlistId);
        }
        if (clientEventId != null) {
            json.append(",\"clientEventId\":\"").append(clientEventId).append('"');
        }
        json.append('}');
        return json.toString();
    }
}
