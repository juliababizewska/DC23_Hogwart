package pl.hogwart.cvprocessor.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.hogwart.cvprocessor.model.Candidate;
import pl.hogwart.cvprocessor.services.CandidateService;
import pl.hogwart.cvprocessor.services.CloudService;

import java.util.List;

/**
 * Class responsible for REST communication with other modules and serving the results to client
 */
@Controller
@RequiredArgsConstructor
public class ClientAPIController {

    private final CandidateService candidateService;
    private final CloudService cloudService;

    @GetMapping("/")
    public String main() {
        return "index";
    }


    @GetMapping("/ranking") // GET http://localhost:8080/ranking
    public String ranking(Model model) {
        List<Candidate> candidates = candidateService.getAllCandidatesSorted();
        candidateService.markMeetsRequirements();
        model.addAttribute("candidates", candidates);
        return "ranking";
    }

    @PostMapping("/notify-candidates")
    @ResponseBody
    public ResponseEntity<String> notifyCandidates() {
        List<Candidate> candidates = candidateService.getAllCandidatesSorted();

        if (candidates.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Brak kandydatów do powiadomienia");
        }
        try {
            for (Candidate candidate : candidates) {
                cloudService.sendResponse(
                        candidate.getEmail(),
                        candidate.getFull_name(),
                        "Nauczyciel OPCzM".equals(candidate.getPosition()),
                        candidate.isMeetsRequirements()
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Błąd podczas wysyłania e-maili: " + e.getMessage());
        }

        return ResponseEntity.ok("Wysłano wiadomości e-mail do kandydatów");
    }

}
