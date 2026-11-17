package net.b3rt1c.music_streaming_backend.dto;

import java.util.ArrayList;
import java.util.List;

import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;
import tools.jackson.databind.JsonNode;

public final class TagAssignment {
    private TagAssignment() {
    }

    /**
     * A string is a free-type tag. An object is {@code { "type": "mood", "name": "gym" }}.
     * A missing type also means free.
     */
    public static List<TagRequest> parse(JsonNode body) {
        if (body == null || body.isNull() || !body.isObject()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "Request body is invalid");
        }
        if (!body.has("tags") || body.get("tags").isNull()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "tags is required");
        }
        JsonNode list = body.get("tags");
        if (!list.isArray()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "tags must be a list");
        }
        List<TagRequest> requests = new ArrayList<>();
        for (JsonNode node : list) {
            requests.add(entry(node));
        }
        return requests;
    }

    private static TagRequest entry(JsonNode node) {
        if (node == null || node.isNull()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "tags must be names or {type, name} objects");
        }
        if (node.isString()) {
            return new TagRequest(null, node.asString());
        }
        if (!node.isObject()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "tags must be names or {type, name} objects");
        }
        if (!node.has("name") || node.get("name").isNull() || !node.get("name").isString()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "tag name is required");
        }
        String type = null;
        if (node.has("type") && !node.get("type").isNull()) {
            if (!node.get("type").isString()) {
                throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "tag type must be a string");
            }
            type = node.get("type").asString();
        }
        return new TagRequest(type, node.get("name").asString());
    }
}
