package net.b3rt1c.music_streaming_backend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.ImportRequest;
import net.b3rt1c.music_streaming_backend.dto.ImportResult;
import net.b3rt1c.music_streaming_backend.service.FolderImportService;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class ImportController {
    private final FolderImportService folderImportService;

    @PostMapping("/import")
    public ImportResult importFolder(@RequestBody(required = false) ImportRequest request) {
        String path = request == null ? null : request.path();
        return folderImportService.importPath(path);
    }
}
