package net.b3rt1c.music_streaming_backend.dto;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;

public final class TagAssignment {
    private TagAssignment() {
    }

    public static List<String> parse(JsonNode body) {
        if (body == null || body.isNull() || !body.isObject()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "Request body is invalid");
        }
        if (!body.has("tags") || body.get("tags").isNull()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "tags is required");
        }
        JsonNode list = body.get("tags");
        if (!list.isArray()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "tags must be a list of strings");
        }
        List<String> names = new ArrayList<>();
        for (JsonNode node : list) {
            if (node == null || node.isNull() || !node.isString()) {
                throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "tags must be a list of strings");
            }
            names.add(node.asString());
        }
        return names;
    }
}
