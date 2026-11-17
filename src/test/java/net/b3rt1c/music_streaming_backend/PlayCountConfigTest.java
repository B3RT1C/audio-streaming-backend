package net.b3rt1c.music_streaming_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;

@SpringBootTest(
    properties = {
        "app.stats.zone=UTC",
        "app.stats.min-counted-seconds=10",
        "app.stats.counted-duration-ratio=0.5",
        "app.stats.listened-slack-seconds=0",
        "spring.datasource.url=jdbc:h2:mem:playcountconfig;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.storage.dir=target/test-storage-stats"
    }
)
@AutoConfigureMockMvc
class PlayCountConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AudioDataRepository audioDataRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM play_events");
        audioDataRepository.deleteAll();
    }

    @Test
    void thresholdSlackAndZoneComeFromConfiguration() throws Exception {
        int unknown = save(null);
        int tenSeconds = save(10);

        mockMvc.perform(post("/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audioId\":" + unknown + ",\"listenedSeconds\":9,\"startedAt\":\"2026-01-15T23:30:00Z\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.counted").value(false));
        mockMvc.perform(post("/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audioId\":" + unknown + ",\"listenedSeconds\":10,\"startedAt\":\"2026-01-15T23:30:00Z\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.counted").value(true));

        mockMvc.perform(post("/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audioId\":" + tenSeconds + ",\"listenedSeconds\":100}"))
            .andExpect(jsonPath("$.listenedSeconds").value(10))
            .andExpect(jsonPath("$.counted").value(true));
        mockMvc.perform(post("/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audioId\":" + tenSeconds + ",\"listenedSeconds\":6}"))
            .andExpect(jsonPath("$.listenedSeconds").value(6))
            .andExpect(jsonPath("$.counted").value(true));
        mockMvc.perform(post("/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audioId\":" + tenSeconds + ",\"listenedSeconds\":4}"))
            .andExpect(jsonPath("$.counted").value(false));

        mockMvc.perform(get("/stats/timeline")
                .param("granularity", "day")
                .param("from", "2026-01-15")
                .param("to", "2026-01-16"))
            .andExpect(jsonPath("$[0].period").value("2026-01-15"))
            .andExpect(jsonPath("$[0].plays").value(1))
            .andExpect(jsonPath("$[0].listenedSeconds").value(19))
            .andExpect(jsonPath("$[1].period").value("2026-01-16"))
            .andExpect(jsonPath("$[1].plays").value(0))
            .andExpect(jsonPath("$[1].listenedSeconds").value(0));
    }

    private int save(Integer duration) {
        AudioData audio = new AudioData("t", UUID.randomUUID().toString(), "mp3", "uploads/songs/");
        audio.setTitle("t");
        audio.setDurationSeconds(duration);
        audio.setCreatedAt(Instant.now());
        return audioDataRepository.save(audio).getId();
    }
}
