package net.b3rt1c.music_streaming_backend.dto;

import tools.jackson.databind.JsonNode;

import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;

/**
 * Partial update. A missing field is left unchanged; JSON null clears optional metadata.
 * secondaryTitle follows the same rules as artist and album.
 */
public record AudioMetadataPatch(
    boolean nameSet,
    String name,
    boolean titleSet,
    String title,
    boolean secondaryTitleSet,
    String secondaryTitle,
    boolean artistSet,
    String artist,
    boolean albumSet,
    String album,
    boolean trackNumberSet,
    Integer trackNumber,
    boolean durationSecondsSet,
    Integer durationSeconds
) {
    public static AudioMetadataPatch parse(JsonNode body) {
        if (body == null || body.isNull()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "Request body is invalid");
        }
        if (!body.isObject()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "Metadata body must be a JSON object");
        }
        return new AudioMetadataPatch(
            body.has("name"),
            text(body, "name", false),
            body.has("title"),
            text(body, "title", false),
            body.has("secondaryTitle"),
            text(body, "secondaryTitle", true),
            body.has("artist"),
            text(body, "artist", true),
            body.has("album"),
            text(body, "album", true),
            body.has("trackNumber"),
            integer(body, "trackNumber", 1),
            body.has("durationSeconds"),
            integer(body, "durationSeconds", 0)
        );
    }

    private static String text(JsonNode body, String field, boolean allowNull) {
        if (!body.has(field)) {
            return null;
        }
        JsonNode node = body.get(field);
        if (node.isNull()) {
            if (allowNull) {
                return null;
            }
            throw ApiException.badRequest(ErrorCodes.INVALID_NAME, "A valid " + field + " is required");
        }
        if (!node.isString()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_NAME, field + " must be a string");
        }
        return node.asString();
    }

    private static Integer integer(JsonNode body, String field, int minimum) {
        if (!body.has(field)) {
            return null;
        }
        JsonNode node = body.get(field);
        if (node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_FILE, field + " must be an integer");
        }
        int value = node.intValue();
        if (value < minimum) {
            throw ApiException.badRequest(
                ErrorCodes.INVALID_FILE,
                field + " must be at least " + minimum
            );
        }
        return value;
    }
}
