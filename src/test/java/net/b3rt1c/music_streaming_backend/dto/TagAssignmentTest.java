package net.b3rt1c.music_streaming_backend.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.b3rt1c.music_streaming_backend.error.ApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TagAssignmentTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void readsStringsAndTypedObjects() throws Exception {
        assertEquals(
            List.of(new TagRequest(null, "Live"), new TagRequest(null, "rock")),
            TagAssignment.parse(read("{\"tags\":[\"Live\",\"rock\"]}"))
        );
        assertEquals(List.of(), TagAssignment.parse(read("{\"tags\":[]}")));
        assertEquals(
            List.of(new TagRequest(null, "Live"), new TagRequest("mood", "gym")),
            TagAssignment.parse(read("{\"tags\":[\"Live\",{\"type\":\"mood\",\"name\":\"gym\"}]}"))
        );
        assertEquals(
            List.of(new TagRequest(null, "gym")),
            TagAssignment.parse(read("{\"tags\":[{\"name\":\"gym\"}]}"))
        );
    }

    @Test
    void rejectsAnythingThatIsNotAStringOrObject() throws Exception {
        assertCode("{}", "INVALID_BODY");
        assertCode("{\"tags\":null}", "INVALID_BODY");
        assertCode("{\"tags\":\"Live\"}", "INVALID_BODY");
        assertCode("{\"tags\":[1]}", "INVALID_BODY");
        assertCode("{\"tags\":[{\"type\":\"mood\"}]}", "INVALID_BODY");
        assertCode("{\"tags\":[{\"type\":1,\"name\":\"gym\"}]}", "INVALID_BODY");
        assertCode("[]", "INVALID_BODY");
        assertThrows(ApiException.class, () -> TagAssignment.parse(null));
    }

    private void assertCode(String body, String code) throws Exception {
        ApiException exception = assertThrows(ApiException.class, () -> TagAssignment.parse(read(body)));
        assertEquals(code, exception.getCode());
    }

    private JsonNode read(String body) throws Exception {
        return json.readTree(body);
    }
}
