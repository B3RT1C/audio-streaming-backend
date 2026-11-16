package net.b3rt1c.music_streaming_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.config.ImportProperties;
import net.b3rt1c.music_streaming_backend.dto.ImportResult;

@Component
@ConditionalOnProperty(prefix = "app.import", name = "path")
@RequiredArgsConstructor
public class FolderImportRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(FolderImportRunner.class);

    private final FolderImportService folderImportService;
    private final ImportProperties importProperties;

    @Override
    public void run(ApplicationArguments args) {
        String path = importProperties.getPath();
        if (path == null || path.isBlank()) {
            return;
        }
        ImportResult result = folderImportService.importPath(path);
        if (result.failed() > 0) {
            log.warn("Folder import finished with {} failed file(s)", result.failed());
        }
    }
}
