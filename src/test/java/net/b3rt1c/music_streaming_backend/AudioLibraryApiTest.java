package net.b3rt1c.music_streaming_backend;

import static org.hamcrest.Matchers.containsString;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistItemRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistRepository;

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

    @BeforeEach
    void reset() throws Exception {
        playlistItemRepository.deleteAll();
        playlistRepository.deleteAll();
        audioDataRepository.deleteAll();
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
            .andExpect(jsonPath("$.name").value("Viejo"));

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
            .andReturn().getResponse().getContentAsString();
        int playlistId = JsonPath.read(created, "$.id");

        mockMvc.perform(get("/playlists"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(playlistId))
            .andExpect(jsonPath("$[0].itemCount").value(0))
            .andExpect(jsonPath("$[0].items").doesNotExist());

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
