package net.b3rt1c.music_streaming_backend;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.repository.ArtistRepository;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistItemRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistRepository;
import net.b3rt1c.music_streaming_backend.repository.TagRepository;

@SpringBootTest
@AutoConfigureMockMvc
class AudioLibraryApiTest {

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

    @BeforeEach
    void reset() throws Exception {
        jdbc.update("DELETE FROM play_events");
        jdbc.update("DELETE FROM audio_tags");
        jdbc.update("DELETE FROM artist_tags");
        jdbc.update("DELETE FROM playlist_tags");
        playlistItemRepository.deleteAll();
        playlistRepository.deleteAll();
        audioDataRepository.deleteAll();
        artistRepository.deleteAll();
        tagRepository.deleteAll();
        deleteChildren(Path.of("target/test-storage"));
        deleteChildren(Path.of("target/test-import"));
    }

    @ParameterizedTest
    @CsvSource({
        "mp3,audio/mpeg",
        "m4a,audio/mp4",
        "aac,audio/aac",
        "flac,audio/flac",
        "wav,audio/wav",
        "ogg,audio/ogg",
        "opus,audio/opus",
        "webm,audio/webm"
    })
    void acceptsAudioExtensionsAndServesTheirContentType(String extension, String contentType) throws Exception {
        byte[] bytes = "wav".equals(extension) ? WavFixture.pcmWav(8000, 1) : new byte[] {1, 2, 3, 4, 5};
        mockMvc.perform(multipart("/audios")
                .file(new MockMultipartFile("file", "tema." + extension, "application/octet-stream", bytes)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").value("File received"));

        int id = idOf("tema." + extension);
        mockMvc.perform(get("/audios/" + id))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, contentType))
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
            .andExpect(content().bytes(bytes));
    }

    @Test
    void rejectsNonAudioUploads() throws Exception {
        mockMvc.perform(multipart("/audios")
                .file(new MockMultipartFile("file", "pagina.html", "text/html", "<p>no</p>".getBytes())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FILE"));

        mockMvc.perform(multipart("/audios")
                .file(new MockMultipartFile("file", "tema.mp3", "text/html", new byte[] {1, 2, 3})))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FILE"));

        mockMvc.perform(post("/audios"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FILE_REQUIRED"));

        assertEquals(0, audioDataRepository.count());
    }

    @Test
    void streamsAByteRangeAndRejectsARangeOutsideTheFile() throws Exception {
        byte[] wav = WavFixture.pcmWav(8000, 1);
        mockMvc.perform(multipart("/audios")
                .file(new MockMultipartFile("file", "rango.wav", "audio/wav", wav)))
            .andExpect(status().isCreated());
        int id = idOf("rango");

        mockMvc.perform(get("/audios/" + id).header(HttpHeaders.RANGE, "bytes=10-29"))
            .andExpect(status().isPartialContent())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/wav"))
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 10-29/" + wav.length))
            .andExpect(content().bytes(java.util.Arrays.copyOfRange(wav, 10, 30)));

        int suffix = 8;
        mockMvc.perform(get("/audios/" + id).header(HttpHeaders.RANGE, "bytes=-" + suffix))
            .andExpect(status().isPartialContent())
            .andExpect(header().string(
                HttpHeaders.CONTENT_RANGE,
                "bytes " + (wav.length - suffix) + "-" + (wav.length - 1) + "/" + wav.length
            ));

        mockMvc.perform(get("/audios/" + id).header(HttpHeaders.RANGE, "bytes=" + wav.length + "-" + (wav.length + 20)))
            .andExpect(status().isRequestedRangeNotSatisfiable())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */" + wav.length))
            .andExpect(jsonPath("$.code").value("RANGE_NOT_SATISFIABLE"));

        mockMvc.perform(get("/audios/" + id).header(HttpHeaders.RANGE, "bytes=abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FILE"));
    }

    @Test
    void readsTagsAndSearchesWithoutCaseOrAccents() throws Exception {
        byte[] tagged = Mp3Fixture.withTags("Corazón", "José", "Álbum", "7/10");
        mockMvc.perform(multipart("/audios")
                .file(new MockMultipartFile("file", "corazon.mp3", "audio/mpeg", tagged)))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/audios").param("q", "jose"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Corazón"))
            .andExpect(jsonPath("$[0].artist").value("José"))
            .andExpect(jsonPath("$[0].album").value("Álbum"))
            .andExpect(jsonPath("$[0].trackNumber").value(7))
            .andExpect(jsonPath("$[0].originalFilename").value("corazon.mp3"))
            .andExpect(jsonPath("$[0].name").value("Corazón"))
            .andExpect(jsonPath("$[0].secondaryTitle").value(nullValue()))
            .andExpect(jsonPath("$[0].tags", contains("Álbum")))
            .andExpect(jsonPath("$[0].createdAt").exists());

        mockMvc.perform(get("/audios").param("q", "CORAZON"))
            .andExpect(jsonPath("$[0].title").value("Corazón"));
        mockMvc.perform(get("/audios").param("q", "album"))
            .andExpect(jsonPath("$[0].album").value("Álbum"));
        mockMvc.perform(get("/audios").param("q", "corazon.mp3"))
            .andExpect(jsonPath("$[0].originalFilename").value("corazon.mp3"));
        mockMvc.perform(get("/audios").param("q", "no-esta"))
            .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(multipart("/audios")
                .file(new MockMultipartFile("file", "Mi Tema.mp3", "audio/mpeg", new byte[] {9, 8, 7}))
                .param("name", "Nombre visible"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/audios").param("q", "Mi Tema.mp3"))
            .andExpect(jsonPath("$[0].title").value("Mi Tema"))
            .andExpect(jsonPath("$[0].name").value("Nombre visible"))
            .andExpect(jsonPath("$[0].artist").value(nullValue()));
    }

    @Test
    void patchUpdatesOnlyTheSentMetadata() throws Exception {
        int id = saveTrack("Viejo", "Viejo título", "Ana", "Disco", "viejo.mp3");

        mockMvc.perform(patch("/audios/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"artist\":\"Banda\",\"album\":null,\"trackNumber\":3,\"title\":\"Nuevo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Nuevo"))
            .andExpect(jsonPath("$.artist").value("Banda"))
            .andExpect(jsonPath("$.album").value(nullValue()))
            .andExpect(jsonPath("$.trackNumber").value(3))
            .andExpect(jsonPath("$.name").value("Viejo"))
            .andExpect(jsonPath("$.secondaryTitle").value(nullValue()))
            .andExpect(jsonPath("$.tags").isEmpty());

        mockMvc.perform(patch("/audios/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_NAME"));

        mockMvc.perform(get("/audios").param("q", "banda"))
            .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void playlistsSupportCreateEditReorderAndRemoval() throws Exception {
        int first = saveTrack("Uno", "Uno", "A", "Disco", "uno.mp3");
        int second = saveTrack("Dos", "Dos", "B", "Disco", "dos.mp3");

        String created = mockMvc.perform(post("/playlists")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Noche\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Noche"))
            .andExpect(jsonPath("$.itemCount").value(0))
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.tags").isEmpty())
            .andReturn().getResponse().getContentAsString();
        int playlistId = JsonPath.read(created, "$.id");

        mockMvc.perform(get("/playlists"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(playlistId))
            .andExpect(jsonPath("$[0].itemCount").value(0))
            .andExpect(jsonPath("$[0].items").doesNotExist())
            .andExpect(jsonPath("$[0].tags").isEmpty());

        mockMvc.perform(patch("/playlists/" + playlistId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Madrugada\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Madrugada"));

        mockMvc.perform(post("/playlists/" + playlistId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audioId\":" + first + "}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.position").value(1))
            .andExpect(jsonPath("$.audioId").value(first))
            .andExpect(jsonPath("$.audio.title").value("Uno"));

        mockMvc.perform(post("/playlists/" + playlistId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audioId\":" + first + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.audioId").value(first));

        mockMvc.perform(post("/playlists/" + playlistId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audioId\":" + second + "}"))
            .andExpect(status().isCreated());

        mockMvc.perform(put("/playlists/" + playlistId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audioIds\":[" + second + "," + first + "]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].audioId").value(second))
            .andExpect(jsonPath("$.items[0].position").value(1))
            .andExpect(jsonPath("$.items[1].audioId").value(first))
            .andExpect(jsonPath("$.items[1].position").value(2));

        mockMvc.perform(put("/playlists/" + playlistId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[" + first + "," + second + "]"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].audioId").value(first))
            .andExpect(jsonPath("$.items[1].audioId").value(second));

        mockMvc.perform(put("/playlists/" + playlistId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audioIds\":[" + first + "]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ORDER"));

        mockMvc.perform(delete("/playlists/" + playlistId + "/items/" + second))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/playlists/" + playlistId))
            .andExpect(jsonPath("$.itemCount").value(1))
            .andExpect(jsonPath("$.items[0].audioId").value(first));

        mockMvc.perform(delete("/playlists/" + playlistId))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/playlists/" + playlistId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void deletingAnAudioRemovesItFromPlaylists() throws Exception {
        int audioId = saveTrack("Tema", "Tema", "Ana", "Disco", "tema.mp3");
        String created = mockMvc.perform(post("/playlists")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cola\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        int playlistId = JsonPath.read(created, "$.id");
        mockMvc.perform(post("/playlists/" + playlistId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audioId\":" + audioId + "}"))
            .andExpect(status().isCreated());

        mockMvc.perform(delete("/audios/" + audioId))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/audios/" + audioId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/playlists/" + playlistId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.itemCount").value(0))
            .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void importsAFolderWithoutDuplicatingOrTouchingOriginals() throws Exception {
        Path library = Path.of("target/test-import/library");
        Path nested = library.resolve("nested");
        Files.createDirectories(nested);
        byte[] wav = WavFixture.pcmWav(8000, 1);
        Path original = library.resolve("a.wav");
        Path copy = library.resolve("z-copy.wav");
        Path mp3 = nested.resolve("b.mp3");
        Path notes = library.resolve("notes.txt");
        Files.write(original, wav);
        Files.write(copy, wav);
        Files.write(mp3, new byte[] {9, 9, 9, 9});
        Files.write(notes, "hola".getBytes());
        long originalSize = Files.size(original);

        mockMvc.perform(post("/admin/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"library\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(2))
            .andExpect(jsonPath("$.skipped").value(1))
            .andExpect(jsonPath("$.ignored").value(1))
            .andExpect(jsonPath("$.failed").value(0));

        assertEquals(originalSize, Files.size(original));
        assertTrue(Files.exists(copy));
        assertTrue(Files.exists(mp3));
        assertTrue(Files.exists(notes));
        try (Stream<Path> stored = Files.list(Path.of("target/test-storage"))) {
            assertEquals(2, stored.filter(Files::isRegularFile).count());
        }

        mockMvc.perform(post("/admin/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"library\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(0))
            .andExpect(jsonPath("$.skipped").value(3))
            .andExpect(jsonPath("$.failed").value(0));
        assertEquals(originalSize, Files.size(original));

        mockMvc.perform(get("/audios").param("q", "a.wav"))
            .andExpect(jsonPath("$[0].title").value("a"))
            .andExpect(jsonPath("$[0].originalFilename").value("a.wav"));

        mockMvc.perform(post("/admin/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"../not-importable\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PATH_NOT_ALLOWED"));
    }

    @Test
    void corsAllowsAnArbitraryOriginAndTheWriteMethods() throws Exception {
        String origin = "http://203.0.113.10:8080";
        mockMvc.perform(get("/audios").header(HttpHeaders.ORIGIN, origin))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));

        mockMvc.perform(options("/audios")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PUT")))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("DELETE")));
    }

    @Test
    void secondaryTitleIsEditableAndSearchable() throws Exception {
        int id = saveTrack("Viejo", "Viejo título", "Ana", "Disco", "viejo.mp3");
        int other = saveTrack("Otro", "Otro", "Luis", "Disco", "otro.mp3");

        mockMvc.perform(patch("/audios/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"secondaryTitle\":\"  Traducción  \"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.secondaryTitle").value("Traducción"))
            .andExpect(jsonPath("$.title").value("Viejo título"))
            .andExpect(jsonPath("$.artist").value("Ana"))
            .andExpect(jsonPath("$.tags").isEmpty());

        mockMvc.perform(get("/audios").param("q", "traduccion"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(id));
        mockMvc.perform(get("/audios").param("q", "TRADUCCION"))
            .andExpect(jsonPath("$[0].id").value(id));

        mockMvc.perform(patch("/audios/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"secondaryTitle\":\"  \"}"))
            .andExpect(jsonPath("$.secondaryTitle").value(nullValue()));
        mockMvc.perform(patch("/audios/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"secondaryTitle\":null}"))
            .andExpect(jsonPath("$.secondaryTitle").value(nullValue()));
        mockMvc.perform(get("/audios").param("q", "traduccion"))
            .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(patch("/audios/" + other)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_NAME"));
    }

    @Test
    void tagsFilterTracksAndStayOnTheResponses() throws Exception {
        int first = saveTrack("Uno", "Uno", "Ana", "Disco", "uno.mp3");
        int second = saveTrack("Dos", "Dos", "Luis", "Disco", "dos.mp3");

        mockMvc.perform(put("/audios/" + first + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\" En Vivo \",\"en vivo\",\"Rock\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(first))
            .andExpect(jsonPath("$.artist").value("Ana"))
            .andExpect(jsonPath("$.tags", contains("En Vivo", "Rock")));

        mockMvc.perform(get("/audios"))
            .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/audios").param("tag", "EN VIVO"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(first))
            .andExpect(jsonPath("$[0].tags", contains("En Vivo", "Rock")));
        mockMvc.perform(get("/audios").param("tag", "en vivo").param("q", "uno"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(first));
        mockMvc.perform(get("/audios").param("tag", "en vivo").param("q", "dos"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("tag", "vivo"))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/audios").param("tag", "   "))
            .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(put("/audios/" + first + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\"  ROCK \",\"Jazz\"]}"))
            .andExpect(jsonPath("$.tags", contains("Jazz", "Rock")));

        mockMvc.perform(put("/audios/" + first + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[]}"))
            .andExpect(jsonPath("$.tags").isEmpty());
        mockMvc.perform(get("/tags"))
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].name").value("En Vivo"))
            .andExpect(jsonPath("$[0].audioCount").value(0))
            .andExpect(jsonPath("$[1].name").value("Jazz"))
            .andExpect(jsonPath("$[1].audioCount").value(0))
            .andExpect(jsonPath("$[2].name").value("Rock"))
            .andExpect(jsonPath("$[2].audioCount").value(0));

        mockMvc.perform(put("/audios/" + second + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\"Rock\"]}"))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/audios/" + second))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/tags"))
            .andExpect(jsonPath("$[2].name").value("Rock"))
            .andExpect(jsonPath("$[2].audioCount").value(0));
        mockMvc.perform(get("/audios/" + second))
            .andExpect(status().isNotFound());

        mockMvc.perform(put("/audios/999999/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\"Rock\"]}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(put("/audios/" + first + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":\"Rock\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_BODY"));
        mockMvc.perform(put("/audios/" + first + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_BODY"));
        mockMvc.perform(put("/audios/" + first + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\"   \"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_NAME"));
        mockMvc.perform(put("/audios/" + first + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\"" + "x".repeat(101) + "\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_NAME"));

        StringBuilder tooMany = new StringBuilder("{\"tags\":[");
        for (int i = 0; i < 101; i++) {
            if (i > 0) {
                tooMany.append(',');
            }
            tooMany.append("\"t").append(i).append('"');
        }
        tooMany.append("]}");
        mockMvc.perform(put("/audios/" + first + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tooMany.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_BODY"));
    }

    @Test
    void artistsAndPlaylistsShareTagsWithoutChangingTrackText() throws Exception {
        int first = saveTrack("Uno", "Uno", "Ana", "Disco", "uno.mp3");
        int second = saveTrack("Dos", "Dos", "ana", "Disco", "dos.mp3");

        mockMvc.perform(patch("/audios/" + first)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"artist\":\"Ana\"}"))
            .andExpect(jsonPath("$.artist").value("Ana"));
        mockMvc.perform(patch("/audios/" + second)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"artist\":\"ana\"}"))
            .andExpect(jsonPath("$.artist").value("ana"));

        mockMvc.perform(get("/artists"))
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].name").value("Ana"))
            .andExpect(jsonPath("$[0].audioCount").value(1))
            .andExpect(jsonPath("$[0].tags").isEmpty())
            .andExpect(jsonPath("$[1].name").value("ana"))
            .andExpect(jsonPath("$[1].audioCount").value(1));

        int ana = artistId("Ana");
        mockMvc.perform(put("/artists/" + ana + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\"Live\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Ana"))
            .andExpect(jsonPath("$.audioCount").value(1))
            .andExpect(jsonPath("$.tags", contains("Live")));
        mockMvc.perform(get("/audios").param("q", "uno"))
            .andExpect(jsonPath("$[0].artist").value("Ana"));

        String created = mockMvc.perform(post("/playlists")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Noche\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        int playlistId = JsonPath.read(created, "$.id");
        mockMvc.perform(post("/playlists/" + playlistId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audioId\":" + first + "}"))
            .andExpect(status().isCreated());
        mockMvc.perform(put("/audios/" + first + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\"Live\"]}"))
            .andExpect(status().isOk());
        mockMvc.perform(put("/playlists/" + playlistId + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\"LIVE\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags", contains("Live")))
            .andExpect(jsonPath("$.items[0].audio.tags", contains("Live")))
            .andExpect(jsonPath("$.items[0].audio.artist").value("Ana"));

        mockMvc.perform(get("/playlists"))
            .andExpect(jsonPath("$[0].tags", contains("Live")))
            .andExpect(jsonPath("$[0].items").doesNotExist());
        mockMvc.perform(get("/tags"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("Live"))
            .andExpect(jsonPath("$[0].audioCount").value(1))
            .andExpect(jsonPath("$[0].artistCount").value(1))
            .andExpect(jsonPath("$[0].playlistCount").value(1));

        mockMvc.perform(patch("/audios/" + first)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"artist\":null}"))
            .andExpect(jsonPath("$.artist").value(nullValue()));
        mockMvc.perform(get("/artists"))
            .andExpect(jsonPath("$[0].name").value("Ana"))
            .andExpect(jsonPath("$[0].audioCount").value(0))
            .andExpect(jsonPath("$[0].tags", contains("Live")))
            .andExpect(jsonPath("$[1].audioCount").value(1));

        mockMvc.perform(put("/artists/999999/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[]}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(put("/playlists/999999/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[]}"))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/playlists/" + playlistId))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/tags"))
            .andExpect(jsonPath("$[0].name").value("Live"))
            .andExpect(jsonPath("$[0].playlistCount").value(0))
            .andExpect(jsonPath("$[0].audioCount").value(1))
            .andExpect(jsonPath("$[0].artistCount").value(1));
    }

    @Test
    void uploadAndImportRegisterArtistsFromTags() throws Exception {
        byte[] tagged = Mp3Fixture.withTags("Corazón", "José", "Álbum", "1");
        mockMvc.perform(multipart("/audios")
                .file(new MockMultipartFile("file", "corazon.mp3", "audio/mpeg", tagged)))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/artists"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("José"))
            .andExpect(jsonPath("$[0].audioCount").value(1));

        Path folder = Path.of("target/test-import/tagged");
        Files.createDirectories(folder);
        Files.write(folder.resolve("otra.mp3"), Mp3Fixture.withTags("Otra", "José", "Álbum", "2"));
        Files.write(folder.resolve("sola.mp3"), Mp3Fixture.withTags("Sola", "Banda", "Álbum", "3"));
        mockMvc.perform(post("/admin/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"tagged\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(2));

        mockMvc.perform(get("/artists"))
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].name").value("Banda"))
            .andExpect(jsonPath("$[0].audioCount").value(1))
            .andExpect(jsonPath("$[1].name").value("José"))
            .andExpect(jsonPath("$[1].audioCount").value(2));
        mockMvc.perform(get("/audios").param("q", "otra.mp3"))
            .andExpect(jsonPath("$[0].artist").value("José"))
            .andExpect(jsonPath("$[0].title").value("Otra"));
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

    private int saveTrack(String name, String title, String artist, String album, String filename) {
        AudioData audio = new AudioData(name, UUID.randomUUID().toString(), "mp3", "uploads/songs/");
        audio.setTitle(title);
        audio.setArtist(artist);
        audio.setAlbum(album);
        audio.setOriginalFilename(filename);
        audio.setCreatedAt(Instant.now());
        return audioDataRepository.save(audio).getId();
    }

    private int idOf(String query) throws Exception {
        String body = mockMvc.perform(get("/audios").param("q", query))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }

    private static void deleteChildren(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder())
                .filter(path -> !path.equals(root))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                });
        }
    }
}
