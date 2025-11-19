package pl.hogwart.cvprocessor.services;

import org.springframework.stereotype.Service;
import pl.hogwart.cvprocessor.model.Candidate;
import pl.hogwart.cvprocessor.model.Position;
import pl.hogwart.cvprocessor.repositories.CandidateRepository;

import java.util.Comparator;
import java.util.List;

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
        return candidate;
    }


    public void markMeetsRequirements() {
        List<Candidate> teachers = findAllMeetsRequirementsCandidates(true);
        List<Candidate> keepers = findAllMeetsRequirementsCandidates(false);
        for  (Candidate candidate : teachers) {
            candidate.setMeetsRequirements(true);
        }
        for  (Candidate candidate : keepers) {
            candidate.setMeetsRequirements(true);
        }
    }

    public List<Candidate> getAllCandidates() {
        return repository.findAll()
                .stream()
                .toList();
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

    public List<Candidate> findAllMeetsRequirementsCandidates(boolean isTeacher) {

        String requiredPosition = isTeacher
                ? "Nauczyciel OPCzM"
                : "Asystent gajowego";

        return repository.findAll().stream()
                .filter(c -> requiredPosition.equals(c.getPosition())) // pozycja
                .filter(c -> c.getScore() >= 20)                        // score >= 20
                .sorted(Comparator.comparing(Candidate::getScore).reversed())
                .limit(3)
                .toList();
    }
}

