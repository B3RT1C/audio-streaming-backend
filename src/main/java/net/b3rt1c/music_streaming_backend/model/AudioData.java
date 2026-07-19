package net.b3rt1c.music_streaming_backend.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AudioData implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Setter
    @Column(nullable = false)
    private String name;

    @Setter
    @JsonIgnore
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Setter
    @Column(nullable = false)
    private String extension;

    @Setter
    @Column(nullable = false)
    private String path;

    @Setter
    @Column(name = "content_hash")
    private String contentHash;

    public AudioData(String name, String storageKey, String extension, String path) {
        this.name = name;
        this.storageKey = storageKey;
        this.extension = extension;
        this.path = path;
    }
}
