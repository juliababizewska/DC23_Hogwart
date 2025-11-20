package pl.hogwart.cvprocessor.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Service;
import pl.hogwart.cvprocessor.model.Applicant;
import pl.hogwart.cvprocessor.model.Candidate;
import pl.hogwart.cvprocessor.model.PythonResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class responsible for CV processing
 * Sends file to the pythonIntegrationService and maps received data to Candidate object
 */
@Service
public class CVService {

    private final PythonIntegrationService pythonService;
    private final CandidateService candidateService;
    private final CloudService cloudService;

    public CVService(PythonIntegrationService pythonService, CandidateService candidateService, CloudService cloudService) {
        this.pythonService = pythonService;
        this.candidateService = candidateService;
        this.cloudService = cloudService;
    }

    // Processes all PDF files from /static/ directory
    public PythonResult processAllCVs() {
        // cloud service test block
        List<Applicant> testList = cloudService.getCVs();
        boolean testFlag = false;
        for (Applicant applicant : testList) {
            cloudService.sendResponse(applicant.getEmail(), testFlag);
            testFlag = !testFlag;
            String[] testArray = {applicant.getPathToCV()};
            cloudService.sendFileToCloud(applicant.getEmail(), testArray);
        }
        // end of cloud service test block

        // run python script to process the CV documents and extract data to JSON files
        // the script searches for documents in 'data/cv_files' directory and  outputs them to 'data/results_json'
        PythonResult result = pythonService.runPythonScript();
        if (!result.isSuccess())
            return result; // python script failed, abort processing

        // for each process data from each extracted JSON
        List<String> cvProcessingLogs = new ArrayList<>();
        try {
            Path folder = Paths.get("data/results_json");
            if (Files.exists(folder)) {

                Files.list(folder)
                    .filter(f -> f.toString().endsWith(".json"))
                    .forEach(path -> {
                        String feedback = processCV(path.getFileName().toString());
                        cvProcessingLogs.add(feedback);
                    });
            }

        } catch (Exception e) {
            cvProcessingLogs.add("Error scanning directory: " + e.getMessage());
        }

        result.addLogs(cvProcessingLogs);
        result.setMessage("Przetwarzanie CV zakończone");
        System.out.println(result.toString());
        return result;
    }

    public String processCV(String filename) {
        try {
            // checking if file wasn't already processed
            Optional<Candidate> existing = candidateService.findBySourceFile(filename);
            if (existing.isPresent()) {
                System.out.println("Skipping " + filename + " — was already processed.");
                return "Skipping " + filename + " — file was already processed.";
            }

            System.out.println("Processing file: " + filename);

            // reading the json file extracted from received CV
            String json = new String(Files.readAllBytes(Paths.get("data/results_json/" + filename)));

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(json);

            // validation
            try (InputStream schemaStream = getClass().getResourceAsStream("/static/schemas/cv_json_schema.json")) {
                JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
                JsonSchema schema = factory.getSchema(schemaStream);

                Set<ValidationMessage> errors = schema.validate(jsonNode);
                if (!errors.isEmpty()) {
                    // TODO: send rejection e-mail?
                    String message = errors.stream()
                            .map(ValidationMessage::getMessage)
                            .collect(Collectors.joining("; "));
                    throw new RuntimeException("Invalid CV JSON (" + filename + "): " + message);
                }
            }

            // mapping JSON to Candidate
            Candidate candidate = mapper.readValue(json, Candidate.class);
            if (candidate.getExperience() != null) {
                candidate.getExperience().forEach(e -> e.setCandidate(candidate));
            }
            if (candidate.getEducation() != null) {
                candidate.getEducation().forEach(edu -> edu.setCandidate(candidate));
            }
            candidate.setSourceFile(filename);

            candidateService.saveCandidate(candidate);
            System.out.println("Processed candidate: " + candidate.getFull_name());
            return "Processed candidate: " + candidate.getFull_name();

        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("$.footer: is missing")) {
                String json = "";
                try {
                    json = new String(Files.readAllBytes(Paths.get("wyniki_json/" + filename)));
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonNode = mapper.readTree(json);
                    cloudService.sendResponseNoRODO(jsonNode.get("email").asText());
                } catch(IOException ioe) {
                    System.err.println("Error scanning directory for .json files: " + ioe.getMessage());
                }

            }
            else {
                System.err.println("Błąd w pliku: " + msg);
            }
            e.printStackTrace();
            return "Error processing CV " + filename + ": " + e.getMessage();
        }
    }
}