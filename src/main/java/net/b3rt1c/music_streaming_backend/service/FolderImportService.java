package net.b3rt1c.music_streaming_backend.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.config.ImportProperties;
import net.b3rt1c.music_streaming_backend.dto.ImportResult;
import net.b3rt1c.music_streaming_backend.util.AudioFormats;
import net.b3rt1c.music_streaming_backend.util.ImportPaths;

@Service
@RequiredArgsConstructor
public class FolderImportService {
    private static final Logger log = LoggerFactory.getLogger(FolderImportService.class);
    private static final int MAX_ERRORS = 100;

    private final ImportProperties importProperties;
    private final AudioIngestService audioIngestService;
    private final AudioFileService audioFileService;

    public ImportResult importPath(String requestedPath) {
        Path folder = ImportPaths.resolve(importProperties.getDir(), requestedPath);
        Path storageRoot = existingStorageRoot();
        List<Path> files = listFiles(folder);

        int imported = 0;
        int skipped = 0;
        int failed = 0;
        int ignored = 0;
        List<String> errors = new ArrayList<>();

        for (Path file : files) {
            String extension = extensionOf(file);
            if (!AudioFormats.isAllowedExtension(extension)) {
                ignored++;
                continue;
            }
            try {
                Path realFile = file.toRealPath();
                if (!realFile.startsWith(folder)) {
                    failed++;
                    addError(errors, file, "File is outside the import folder");
                    continue;
                }
                if (storageRoot != null && realFile.startsWith(storageRoot)) {
                    ignored++;
                    continue;
                }
                IngestStatus status = audioIngestService.importLocalFile(realFile);
                if (status == IngestStatus.IMPORTED) {
                    imported++;
                } else {
                    skipped++;
                }
            } catch (Exception exception) {
                failed++;
                String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
                addError(errors, file, message);
                log.warn("Import failed for {}: {}", file, message);
            }
        }

        ImportResult result = new ImportResult(imported, skipped, failed, ignored, errors);
        log.info(
            "Import of {} finished: imported={} skipped={} failed={} ignored={}",
            folder,
            imported,
            skipped,
            failed,
            ignored
        );
        return result;
    }

    private Path existingStorageRoot() {
        Path storage = audioFileService.storageDirectory();
        if (!Files.isDirectory(storage)) {
            return null;
        }
        try {
            return storage.toRealPath();
        } catch (IOException exception) {
            return null;
        }
    }

    private static List<Path> listFiles(Path folder) {
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk
                .filter(Files::isRegularFile)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read import folder", exception);
        }
    }

    private static String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1);
    }

    private static void addError(List<String> errors, Path file, String message) {
        if (errors.size() >= MAX_ERRORS) {
            return;
        }
        String text = file + ": " + message;
        errors.add(text.length() <= 500 ? text : text.substring(0, 500));
    }
}
