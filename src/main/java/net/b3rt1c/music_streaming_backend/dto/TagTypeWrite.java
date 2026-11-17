package net.b3rt1c.music_streaming_backend.dto;

import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;
import tools.jackson.databind.JsonNode;

public final class TagTypeWrite {
    private TagTypeWrite() {
    }

    public record Create(String name, String color) {
    }

    public record Patch(boolean nameSet, String name, boolean colorSet, String color) {
    }

    public static Create parseCreate(JsonNode body) {
        JsonNode object = object(body);
        if (!object.has("name") || object.get("name").isNull()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "name is required");
        }
        return new Create(text(object, "name"), object.has("color") ? text(object, "color") : null);
    }

    public static Patch parsePatch(JsonNode body) {
        JsonNode object = object(body);
        return new Patch(object.has("name"), text(object, "name"), object.has("color"), text(object, "color"));
    }

    private static JsonNode object(JsonNode body) {
        if (body == null || body.isNull() || !body.isObject()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "Request body is invalid");
        }
        return body;
    }

    private static String text(JsonNode body, String field) {
        if (!body.has(field) || body.get(field).isNull()) {
            return null;
        }
        JsonNode node = body.get(field);
        if (!node.isString()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, field + " must be a string");
        }
        return node.asString();
    }
}
