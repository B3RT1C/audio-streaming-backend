package net.b3rt1c.music_streaming_backend.service;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import net.b3rt1c.music_streaming_backend.dto.RebuildResult;
import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.model.Tag;
import net.b3rt1c.music_streaming_backend.model.TagType;
import net.b3rt1c.music_streaming_backend.repository.ArtistRepository;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistRepository;
import net.b3rt1c.music_streaming_backend.repository.TagRepository;
import net.b3rt1c.music_streaming_backend.repository.TagTypeRepository;
import net.b3rt1c.music_streaming_backend.util.Names;
import net.b3rt1c.music_streaming_backend.util.TagNames;
import net.b3rt1c.music_streaming_backend.util.TagTypeKeys;

@Service
public class SystemTagService {
    private enum Outcome {
        UPDATED,
        UNCHANGED,
        FAILED
    }

    private final AudioDataRepository audioDataRepository;
    private final TagRepository tagRepository;
    private final TagTypeRepository tagTypeRepository;
    private final ArtistRepository artistRepository;
    private final PlaylistRepository playlistRepository;
    private final AudioFileService audioFileService;
    private final AudioMetadataReader audioMetadataReader;
    private final TransactionTemplate transactions;

    public SystemTagService(
            AudioDataRepository audioDataRepository,
            TagRepository tagRepository,
            TagTypeRepository tagTypeRepository,
            ArtistRepository artistRepository,
            PlaylistRepository playlistRepository,
            AudioFileService audioFileService,
            AudioMetadataReader audioMetadataReader,
            PlatformTransactionManager transactionManager) {
        this.audioDataRepository = audioDataRepository;
        this.tagRepository = tagRepository;
        this.tagTypeRepository = tagTypeRepository;
        this.artistRepository = artistRepository;
        this.playlistRepository = playlistRepository;
        this.audioFileService = audioFileService;
        this.audioMetadataReader = audioMetadataReader;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public void syncDerived(AudioData audio) {
        audio.getTags().size();
        syncOne(audio, requireType(TagTypeKeys.GENRE), audio.getGenre());
        syncOne(audio, requireType(TagTypeKeys.ALBUM), audio.getAlbum());
        syncOne(audio, requireType(TagTypeKeys.YEAR), audio.getYear() == null ? null : Integer.toString(audio.getYear()));
    }

    /**
     * Rereads every stored file and realigns genre, year and the derived tags.
     * Title, secondary title and artist are left as they are. An album already stored is kept;
     * a blank album is filled from the file. A missing or unreadable file is counted and skipped.
     * Running it again with the same files changes nothing.
     */
    public RebuildResult rebuild() {
        List<Integer> ids = audioDataRepository.findIds();
        int updated = 0;
        int unchanged = 0;
        int failed = 0;
        for (Integer id : ids) {
            Outcome outcome = transactions.execute(status -> rebuildOne(id));
            if (outcome == Outcome.UPDATED) {
                updated++;
            } else if (outcome == Outcome.FAILED) {
                failed++;
            } else {
                unchanged++;
            }
        }
        transactions.executeWithoutResult(status -> deleteUnusedDerivedTags());
        return new RebuildResult(ids.size(), updated, unchanged, failed);
    }

    private Outcome rebuildOne(int id) {
        AudioData audio = audioDataRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Audio track not found"));
        audio.getTags().size();
        String title = audio.getTitle();
        String secondary = audio.getSecondaryTitle();
        String artist = audio.getArtist();
        String album = audio.getAlbum();
        String genre = audio.getGenre();
        Integer year = audio.getYear();
        String before = derivedSignature(audio);

        Path file = audioFileService.resolveStoredFile(audio.getPath(), audio.getStorageKey(), audio.getExtension());
        Optional<AudioMetadata> read = audioMetadataReader.readStored(file);
        if (read.isEmpty()) {
            return Outcome.FAILED;
        }
        AudioMetadata metadata = read.get();
        audio.setGenre(metadata.genre());
        audio.setYear(metadata.year());
        if ((album == null || album.isBlank()) && metadata.album() != null && !metadata.album().isBlank()) {
            audio.setAlbum(metadata.album());
        }
        restoreIdentity(audio, title, secondary, artist);
        syncDerived(audio);
        restoreIdentity(audio, title, secondary, artist);
        audioDataRepository.save(audio);

        boolean changed = !Objects.equals(album, audio.getAlbum())
            || !Objects.equals(genre, audio.getGenre())
            || !Objects.equals(year, audio.getYear())
            || !before.equals(derivedSignature(audio));
        return changed ? Outcome.UPDATED : Outcome.UNCHANGED;
    }

    private void deleteUnusedDerivedTags() {
        Set<Integer> used = new HashSet<>();
        collect(used, audioDataRepository.countByTag());
        collect(used, artistRepository.countByTag());
        collect(used, playlistRepository.countByTag());
        List<Tag> unused = tagRepository.findAllWithType().stream()
            .filter(tag -> tag.getType() != null && TagTypeKeys.derived(tag.getType().getTypeKey()))
            .filter(tag -> !used.contains(tag.getId()))
            .toList();
        if (!unused.isEmpty()) {
            tagRepository.deleteAll(unused);
        }
    }

    private void syncOne(AudioData audio, TagType type, String rawName) {
        Tag desired = findOrCreate(type, rawName);
        String key = type.getTypeKey();
        List<Tag> existing = audio.getTags().stream()
            .filter(tag -> tag.getType() != null && key.equals(tag.getType().getTypeKey()))
            .toList();
        for (Tag tag : existing) {
            if (desired == null || !tag.getId().equals(desired.getId())) {
                audio.getTags().remove(tag);
            }
        }
        if (desired != null && audio.getTags().stream().noneMatch(tag -> desired.getId().equals(tag.getId()))) {
            audio.getTags().add(desired);
        }
    }

    private Tag findOrCreate(TagType type, String rawName) {
        String name = Names.clip(rawName, TagNames.MAX_LENGTH);
        if (name == null) {
            return null;
        }
        String nameKey = TagNames.key(name);
        return tagRepository.findByTypeAndNameKey(type, nameKey).orElseGet(() -> {
            Tag created = new Tag();
            created.setType(type);
            created.setName(name);
            created.setNameKey(nameKey);
            return tagRepository.save(created);
        });
    }

    private TagType requireType(String key) {
        return tagTypeRepository.findByTypeKey(key)
            .orElseThrow(() -> new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCodes.INTERNAL,
                "System tag type is missing"
            ));
    }

    private static void restoreIdentity(AudioData audio, String title, String secondary, String artist) {
        audio.setTitle(title);
        audio.setSecondaryTitle(secondary);
        audio.setArtist(artist);
    }

    private static String derivedSignature(AudioData audio) {
        return audio.getTags().stream()
            .filter(tag -> tag.getType() != null && TagTypeKeys.derived(tag.getType().getTypeKey()))
            .map(tag -> tag.getType().getTypeKey() + "=" + tag.getNameKey())
            .sorted(Comparator.naturalOrder())
            .reduce((left, right) -> left + "|" + right)
            .orElse("");
    }

    private static void collect(Set<Integer> used, List<Object[]> rows) {
        for (Object[] row : rows) {
            used.add(((Number) row[0]).intValue());
        }
    }
}
