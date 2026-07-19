package net.b3rt1c.music_streaming_backend.service;

import java.util.List;

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
        return audioDataRepository.findById(id).orElse(null);
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
