package pl.hogwart.cvprocessor.controllers;

import lombok.RequiredArgsConstructor;
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
    public String notifyCandidates() {
        List<Candidate> candidates = candidateService.getAllCandidatesSorted();

        for (Candidate candidate : candidates) {
            cloudService.sendResponse(
                    candidate.getEmail(),
                    candidate.getFull_name(),
                    candidate.getPosition().compareTo("Nauczyciel OPCzM") == 0,
                    candidate.isMeetsRequirements()
            );
        }
        return "Wysłano wiadomości e-mail do kandydatów. [TODO: ClientAPIController.java]";
    }

}
