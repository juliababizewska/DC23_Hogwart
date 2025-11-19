package pl.hogwart.cvprocessor.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.hogwart.cvprocessor.model.Candidate;
import pl.hogwart.cvprocessor.services.CandidateService;

import java.util.List;

/**
 * Class responsible for REST communication with other modules and serving the results to client
 */
@Controller
@RequiredArgsConstructor
public class ClientAPIController {

    private final CandidateService candidateService;

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
        // TODO: Google API - send emails
        return "Wysłano wiadomości e-mail do kandydatów. [TODO: ClientAPIController.java]";
    }

}
