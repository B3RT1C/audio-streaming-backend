package net.b3rt1c.music_streaming_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.StatsRankResponse;
import net.b3rt1c.music_streaming_backend.dto.StatsSummaryResponse;
import net.b3rt1c.music_streaming_backend.dto.StatsTimelinePoint;
import net.b3rt1c.music_streaming_backend.service.StatsService;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {
    private final StatsService statsService;

    @GetMapping("/summary")
    public StatsSummaryResponse summary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return statsService.summary(from, to);
    }

    @GetMapping("/top")
    public List<StatsRankResponse> top(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String tagType) {
        return statsService.top(kind, from, to, limit, tagType);
    }

    @GetMapping("/timeline")
    public List<StatsTimelinePoint> timeline(
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return statsService.timeline(granularity, from, to);
    }
}
