package pl.hogwart.cvprocessor.services;

import org.springframework.stereotype.Service;
import pl.hogwart.cvprocessor.model.Candidate;
import pl.hogwart.cvprocessor.model.Position;
import pl.hogwart.cvprocessor.repositories.CandidateRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Class responsible for managing candidate database
 */
@Service
public class CandidateService {
    private final CandidateRepository repository;

    public CandidateService(CandidateRepository repository) {
        this.repository = repository;
    }

    public Candidate calculateScore(Candidate candidate) {
        // assigning random score
        candidate.setMeetsRequirements(true);
        if (candidate.isMeetsRequirements()) {
            double teacherScore = SkillsValidatorService.calculateScoreForPosition(candidate,  Position.TEACHER);
            double keeperScore = SkillsValidatorService.calculateScoreForPosition(candidate,  Position.KEEPER);

            if(teacherScore == 0.0 && keeperScore == 0.0){
                candidate.setPosition("Brak dopasowania");
                candidate.setScore(0.0);
                candidate.setMeetsRequirements(false); // TODO: CHANGE TO SET TRUE ONLY TO THOSE WHO PASSED REQUIREMENTS
            }
            else if (teacherScore > keeperScore) {
                candidate.setPosition("Nauczyciel OPCzM");
                candidate.setScore(teacherScore);
            }
            else {
                candidate.setPosition("Asystent gajowego");
                candidate.setScore(keeperScore);
            }
        }
        return candidate;
    }

    public List<Candidate> getAllCandidates() {
        return repository.findAll()
                .stream()
                .toList();
    }

    public Optional<Candidate> findBySourceFile(String sourceFile) {
        return repository.findBySourceFile(sourceFile);
    }

    public List<Candidate> getAllCandidatesSorted() {
        return repository.findAll()
                .stream()
                .sorted(
                    Comparator.comparing(Candidate::isMeetsRequirements).reversed()
                            .thenComparing(Comparator.comparingDouble(Candidate::getScore).reversed())
                )
                .toList();
    }

    public Candidate saveCandidate(Candidate candidate) {
        candidate = calculateScore(candidate);
        return repository.save(candidate);
    }

    public void clear() { repository.deleteAll(); }
}

