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

@Service
@RequiredArgsConstructor
public class AudioStreamService {
    private final AudioFileService audioFileService;

    public ResponseEntity<ResourceRegion> streamAudioFile(String path, String name, String extension, HttpHeaders headers)
            throws IOException {
        Resource resource = audioFileService.getAudioFile(path, name, extension);

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        long contentLength = resource.contentLength();
        List<HttpRange> ranges = headers.getRange();

        if (ranges.isEmpty()) {
            return ResponseEntity.ok()
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .contentLength(contentLength)
                .body(new ResourceRegion(resource, 0, contentLength));
        }

        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(contentLength);
        long end = range.getRangeEnd(contentLength);
        long rangeLength = end - start + 1;

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .contentType(MediaType.parseMediaType("audio/mpeg"))
            .contentLength(rangeLength)
            .body(new ResourceRegion(resource, start, rangeLength));
    }
}
