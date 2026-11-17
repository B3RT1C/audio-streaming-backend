package net.b3rt1c.music_streaming_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.FilterSuggestion;
import net.b3rt1c.music_streaming_backend.service.LibraryFilterService;

@RestController
@RequestMapping("/filter")
@RequiredArgsConstructor
public class FilterController {
    private final LibraryFilterService libraryFilterService;

    @GetMapping("/suggest")
    public List<FilterSuggestion> suggest(
            @RequestParam(value = "prefix", required = false) String prefix,
            @RequestParam(value = "q", required = false) String query) {
        return libraryFilterService.suggest(prefix, query);
    }
}
