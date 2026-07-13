package net.b3rt1c.music_streaming_backend.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;

@Service
@RequiredArgsConstructor
public class AudioDataService {
    private final AudioDataRepository audioDataRepository;

    public List<AudioData> findAllAudioDatas() {
        return audioDataRepository.findAll();
    }

    public AudioData findAudioData(Integer id) {
        Optional<AudioData> audioData = audioDataRepository.findById(id);

        if (audioData.isEmpty()) {
            return null;
        }

        return audioData.get();
    }

    public boolean existsByName(String name) {
        return audioDataRepository.existsByName(name);
    }

    public boolean existsByContentHash(String contentHash) {
        return audioDataRepository.existsByContentHash(contentHash);
    }

    public void addAudioData(AudioData audioData) {
        audioDataRepository.save(audioData);
    }

    public void deleteAudioData(Integer id) {
        audioDataRepository.deleteById(id);
    }
}
