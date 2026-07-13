package net.b3rt1c.music_streaming_backend.model;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor @AllArgsConstructor
public class AudioData implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Setter
    @Column(unique = true, nullable = false)
    private String name;

    @Setter
    @Column(nullable = false)
    private String extension;

    @Setter
    @Column(nullable = false)
    private String path;

    @Setter
    @Column(name = "content_hash")
    private String contentHash;

    public AudioData(String name, String extension, String path) {
        this.name = name;
        this.extension = extension;
        this.path = path;
    }
}