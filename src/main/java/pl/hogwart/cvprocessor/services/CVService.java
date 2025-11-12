package pl.hogwart.cvprocessor.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.stereotype.Service;
import pl.hogwart.cvprocessor.model.Applicant;
import pl.hogwart.cvprocessor.model.Candidate;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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
    public void processAllCVs() {
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

        try {
            Path folder = Paths.get("wyniki_json");
            if (Files.exists(folder)) {
                Files.list(folder)
                        .filter(f -> f.toString().endsWith(".json"))
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .forEach(this::processCV);
            }
        } catch (Exception e) {
            System.err.println("Error scanning directory for .json files: " + e.getMessage());
        }
    }

    public String processCV(String filename) {
        try {
            System.out.println("Processing file: " + filename);
            // run python script to process the file and get back candidate's data in JSON
            //String json = pythonService.runPythonScript(filePath.toAbsolutePath().toString());
            String json = new String(Files.readAllBytes(Paths.get("wyniki_json/" + filename)));

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(json);

            // validation
            try (InputStream schemaStream = getClass().getResourceAsStream("/schemas/cv_json_schema.json")) {
                JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
                JsonSchema schema = factory.getSchema(schemaStream);

                Set<ValidationMessage> errors = schema.validate(jsonNode);
                if (!errors.isEmpty()) {
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
            e.printStackTrace();
            return "Error processing CV " + filename + ": " + e.getMessage();
        }
    }
}