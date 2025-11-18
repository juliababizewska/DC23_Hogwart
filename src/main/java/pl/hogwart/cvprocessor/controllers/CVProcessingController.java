package pl.hogwart.cvprocessor.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import pl.hogwart.cvprocessor.model.Candidate;
import pl.hogwart.cvprocessor.services.CVService;
import pl.hogwart.cvprocessor.services.CandidateService;

/**
 * Class responsible for access to candidates database through REST
 */
@Controller
@RequiredArgsConstructor
public class CVProcessingController {

    private final CVService cvService;
    private final CandidateService candidateService;

    // saving candidate to database
    @PostMapping("/candidates") // POST to http://localhost:8080/candidates
    public ResponseEntity<Candidate> addCandidate(@RequestBody Candidate candidate) {
        Candidate saved = candidateService.saveCandidate(candidate);
        return ResponseEntity.ok(saved);
    }

    // deleting all records from database
    @PostMapping("/candidates/clear")
    @ResponseBody
    public String clearTable() {
        candidateService.clear();
        return "Usunięto dane z bazy kandydatów!";
    }

    // running cv processing test
    @PostMapping("/process-test")
    @ResponseBody
    public String processTestCVs() {
        cvService.processAllCVs();
        return "Processed all CVs from /static/";
    }

    @PostMapping("/process")
    @ResponseBody
    public String processAllCVs() {
        cvService.processAllCVs();
        return "Processed all received CVs [now static, downloading module is not connected yet]";
    }
}
