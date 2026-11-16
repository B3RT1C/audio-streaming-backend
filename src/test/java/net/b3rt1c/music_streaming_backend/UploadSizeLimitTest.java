package net.b3rt1c.music_streaming_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.servlet.multipart.max-file-size=1KB",
        "spring.servlet.multipart.max-request-size=8KB",
        "server.tomcat.max-http-form-post-size=8KB",
        "server.tomcat.max-swallow-size=8KB",
        "spring.datasource.url=jdbc:h2:mem:uploadlimit;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.storage.dir=target/test-storage-limit"
    }
)
@AutoConfigureMockMvc
class UploadSizeLimitTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void oversizedUploadIsFileTooLargeInsteadOfFileRequired() throws Exception {
        byte[] audio = new byte[4096];
        byte[] body = multipart(audio);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/audios"))
            .header("Content-Type", "multipart/form-data; boundary=----Bound")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(413, response.statusCode(), response.body());
        assertTrue(response.body().contains("FILE_TOO_LARGE"), response.body());
        assertFalse(response.body().contains("FILE_REQUIRED"), response.body());

        mockMvc.perform(get("/audios"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    private static byte[] multipart(byte[] audio) {
        String header = """
            ------Bound\r
            Content-Disposition: form-data; name="file"; filename="grande.mp3"\r
            Content-Type: audio/mpeg\r
            \r
            """;
        String footer = "\r\n------Bound--\r\n";
        byte[] prefix = header.getBytes(StandardCharsets.UTF_8);
        byte[] suffix = footer.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefix.length + audio.length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(audio, 0, body, prefix.length, audio.length);
        System.arraycopy(suffix, 0, body, prefix.length + audio.length, suffix.length);
        return body;
    }
}
