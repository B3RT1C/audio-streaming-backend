package net.b3rt1c.music_streaming_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class helloWorld {
    @GetMapping
    public String getTest() {
        return "Hello World!";
    }
}
