package net.b3rt1c.music_streaming_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "tag_types",
    uniqueConstraints = @UniqueConstraint(name = "tag_types_type_key_key", columnNames = "type_key")
)
@Getter
@Setter
@NoArgsConstructor
public class TagType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "type_key", nullable = false, length = 100)
    private String typeKey;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(length = 32)
    private String color;
}
