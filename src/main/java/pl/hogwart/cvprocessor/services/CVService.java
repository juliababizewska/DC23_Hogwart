package pl.hogwart.cvprocessor.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.hogwart.cvprocessor.model.Candidate;
import pl.hogwart.cvprocessor.model.ProcessedFile;
import pl.hogwart.cvprocessor.model.PythonResult;
import pl.hogwart.cvprocessor.repositories.ProcessedFileRepository;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class responsible for CV processing
 * Sends file to the pythonIntegrationService and maps received data to Candidate object
 */
@Service
@RequiredArgsConstructor
public class CVService {

    private final PythonIntegrationService pythonService;
    private final CandidateService candidateService;
    private final CloudService cloudService;
    private final ProcessedFileRepository processedFileRepository;

    // Processes all PDF files from /static/ directory
    public PythonResult processAllCVs() {
        // Fetching cvs from mail inbox and sending them to cloud archive
        String[] testList = cloudService.getCVs().toArray(new String[0]);
        cloudService.sendFilesToCloud("CVs", testList, false);

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
        System.out.println(result.getMessage() + "\n");
        return result;
    }

    public String processCV(String filename) {
        try {
            // checking if file wasn't already processed
            if (processedFileRepository.existsById(filename)) {
                String feedback = "Skipping " + filename + " — file already processed (" +
                        processedFileRepository.findById(filename).get().getStatus() + ").";
                System.out.println(feedback);
                return feedback;
            }

            System.out.println("Processing file: " + filename);

            // reading the json file extracted from received CV
            String path = "data/results_json/" + filename;
            String json = new String(Files.readAllBytes(Paths.get(path)));

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(json);

            // validation
            InputStream schemaStream = getClass().getResourceAsStream("/static/schemas/cv_json_schema.json");
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            JsonSchema schema = factory.getSchema(schemaStream);

            Set<ValidationMessage> errors = schema.validate(jsonNode);
            if (!errors.isEmpty()) {
                cloudService.sendFilesToCloud("CV_Schemas_Invalid", new String[]{path}, false);
                String message = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.joining("; "));
                if (message != null && message.contains("$.footer: is missing")) {
                    cloudService.sendResponseNoRODO(jsonNode.get("email").asText());
                }
                else {
                    cloudService.sendResponseInvalidSchema(jsonNode.get("email").asText(), message);
                }
                throw new RuntimeException(message + "\n Wysyłanie wiadomości zwrotnej do " + jsonNode.get("email"));
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
            cloudService.sendFilesToCloud("CV_Schemas_Valid", new String[]{path}, false);

            processedFileRepository.save(new ProcessedFile(filename, LocalDateTime.now().toString(), "valid"));
            return "Processed candidate: " + candidate.getFull_name();

        } catch (Exception e) {
            System.err.println("Error processing CV " + filename + ": " + e.getMessage());
            processedFileRepository.save(new ProcessedFile(filename, LocalDateTime.now().toString(), "invalid"));
            return "Error processing CV " + filename + ": " + e.getMessage();
        }
    }
}