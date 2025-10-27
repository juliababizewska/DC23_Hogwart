package pl.hogwart.cvprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CvProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CvProcessorApplication.class, args);
        System.out.println("Client API: http://localhost:8080/"); // check controllers for mappings
        System.out.println("Database console: http://localhost:8080/h2-console (jdbc url: jdbc:h2:file:./data/hogwartdb)"); // details in application.properties
    }
}
