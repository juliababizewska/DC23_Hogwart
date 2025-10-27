package pl.hogwart.cvprocessor.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
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

    // TODO: create another controller for google api stuff?

    @GetMapping("/")
    public String main() {
        return "index";
    }


    @GetMapping("/ranking") // GET http://localhost:8080/ranking
    public String ranking(Model model) {
        List<Candidate> candidates = candidateService.getAllCandidatesSorted();
        model.addAttribute("candidates", candidates);
        return "ranking";
    }
}
