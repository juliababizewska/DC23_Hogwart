package pl.hogwart.cvprocessor.services;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import pl.hogwart.cvprocessor.model.Candidate;
import pl.hogwart.cvprocessor.repositories.CandidateRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Class responsible for managing candidate database
 */
@Service
public class CandidateService {
    private final CandidateRepository repository;

    public CandidateService(CandidateRepository repository) {
        this.repository = repository;
    }

    // TODO: prepare and call functions to check requirements and assign scores to candidates
    public Candidate calculateScore(Candidate candidate) {
        // assigning random score
        candidate.setMeetsRequirements(true);
        if (candidate.isMeetsRequirements()) {
            Double score = new Random().nextDouble(100);
            score = Math.floor(score * 100) / 100;
            candidate.setScore(score);
        }
        return candidate;
    }

    public List<Candidate> getAllCandidates() {
        return repository.findAll()
                .stream()
                .toList();
    }

    public List<Candidate> getAllCandidatesSorted() {
        return repository.findAll()
                .stream()
                .sorted(Comparator.comparingDouble(Candidate::getScore).reversed())
                .toList();
    }

    public Candidate saveCandidate(Candidate candidate) {
        candidate = calculateScore(candidate);
        return repository.save(candidate);
    }

    public void clear() { repository.deleteAll(); }
}

