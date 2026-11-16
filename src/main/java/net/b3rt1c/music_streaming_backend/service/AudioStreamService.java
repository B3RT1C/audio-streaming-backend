package net.b3rt1c.music_streaming_backend.service;

import java.io.IOException;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;
import net.b3rt1c.music_streaming_backend.error.RangeNotSatisfiableException;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.util.AudioFormats;

@Service
@RequiredArgsConstructor
public class AudioStreamService {
    private final AudioFileService audioFileService;

    public ResponseEntity<ResourceRegion> stream(AudioData audioData, HttpHeaders headers) throws IOException {
        Resource resource = audioFileService.getAudioFile(
            audioData.getPath(),
            audioData.getStorageKey(),
            audioData.getExtension()
        );
        if (!resource.exists() || !resource.isReadable()) {
            throw ApiException.notFound("Audio file not found");
        }

        long contentLength = resource.contentLength();
        MediaType mediaType = MediaType.parseMediaType(AudioFormats.contentType(audioData.getExtension()));
        List<HttpRange> ranges = readRanges(headers);
        if (ranges.isEmpty()) {
            return ResponseEntity.ok()
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(mediaType)
                .contentLength(contentLength)
                .body(new ResourceRegion(resource, 0, contentLength));
        }

        HttpRange range = ranges.get(0);
        long start;
        long end;
        try {
            start = range.getRangeStart(contentLength);
            end = range.getRangeEnd(contentLength);
        } catch (IllegalArgumentException exception) {
            throw new RangeNotSatisfiableException(contentLength);
        }
        if (start < 0 || end < start || start >= contentLength) {
            throw new RangeNotSatisfiableException(contentLength);
        }

        long rangeLength = end - start + 1;
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .contentType(mediaType)
            .contentLength(rangeLength)
            .body(new ResourceRegion(resource, start, rangeLength));
    }

    private static List<HttpRange> readRanges(HttpHeaders headers) {
        try {
            return headers.getRange();
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(ErrorCodes.INVALID_FILE, "Invalid range");
        }
    }
}
