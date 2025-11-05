package pl.hogwart.cvprocessor.services;

import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import jakarta.mail.search.FlagTerm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.hogwart.cvprocessor.model.Applicant;

import jakarta.mail.*;
import jakarta.mail.internet.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service handles all action related to google account or google api.
 */

@Service
public class CloudService {
    private static String subjectRegex = "Hogwart Rekrutacja.*";
    private static String saveDir = "src/main/resources/cvs";

    //Credentials to google account
    @Value("${google.account.app-password}")
    private String password;

    @Value("${google.account.mail}")
    private String accountMail;

    @Value("${google.drive.key-path}")
    private String keyPath;

    @Value("${google.drive.root.folder}")
    private String rootFolderId;

    private Drive drive;

    public CloudService(){
        // Checking and creating a folder for downloaded attachments
        try{
            Files.createDirectories(Paths.get(saveDir));
        }
        catch(Exception e){
            System.out.println("Critical ERROR: Couldn't create download directory!!!");
            System.out.println(e.getMessage());
        }
        // initializing Google Drive connection
        try{
            final GsonFactory gsonFactory = GsonFactory.getDefaultInstance();
            final NetHttpTransport transport = new NetHttpTransport();

            GoogleCredentials credentials = ServiceAccountCredentials.fromStream(
                    new FileInputStream(Paths.get(keyPath).toFile()))
                    .createScoped(Collections.singleton((DriveScopes.DRIVE_FILE)));

            HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

            drive = new Drive.Builder(
                    transport,
                    gsonFactory,
                    requestInitializer)
                    .setApplicationName("HogwartCVProcessor")
                    .build();
            System.out.println("Google Drive initialized");
        }
        catch(Exception e){
            System.out.println("Critical ERROR: Couldn't connect to Google Drive!!!");
            System.out.println(e.getMessage());
        }
    }

    // Method establishes connection to the email server
    private Store establishImapConnection() throws MessagingException {
        Properties props = System.getProperties();
        props.setProperty("mail.store.protocol", "imaps");
        props.put("mail.imaps.ssl.enabled", "true");

        Session session = Session.getDefaultInstance(props, null);
        Store store = session.getStore("imaps");
        store.connect("imap.googlemail.com", accountMail, password);
        return store;
    }

    // Method establishes SMTP connection for sending out emails
    private Session establishSmtpConnection() throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.googlemail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(accountMail, password);
            }
        });
        return session;
    }

    private String createResponseText(boolean accepted){
        String text = "Szanowna/y Pani/e, \n\n";

        if(accepted) {
            text = text + "Gratulujemy przejścia do następnego etapu rekrutacji.";
        }
        else{
            text = text + "Z przykrością informujemy że odrzuciliśmy twoją kandydaturę na aplikowane "
                    + "stanowisko. Życzymy powodzenia w dalszych staraniach o zabezpieczenie zatrudnienia.";
        }

        text = text + "\n\n Pozdrawiam, \n Automatyczny system rozpatrzeń CV HogwartCVProcessor";
        return text;
    }

    // Method downloads attachments to specified folder
    private String getAttachment(Message message) {
        try {
            Object content = message.getContent();

            if(content instanceof Multipart){
                Multipart multipart = (Multipart) content;
                for(int i = 0; i< multipart.getCount(); i++) {
                    Part part =  multipart.getBodyPart(i);

                    if(Part.ATTACHMENT.equals(part.getDisposition())) {
                        String fileName = part.getFileName();

                        Path savePath = Paths.get(saveDir + File.separator + fileName);
                        try(InputStream ins = part.getInputStream()) {
                            Files.copy(ins, savePath, StandardCopyOption.REPLACE_EXISTING);
                            return savePath.toString();
                        }
                    }
                }
            }

        }
        catch (Exception e) {
            System.out.println("Error: Failed to download attachment.");
            System.out.println(e.getMessage());
        }
        return null;
    }

    // Method retrieves CVs from email via IMAP
    public List<Applicant> getCVs() {
        List<Applicant> applicants = new ArrayList<>();

        try (Store store = establishImapConnection()) {
            Folder inbox = store.getFolder("inbox");
            inbox.open(Folder.READ_WRITE);
            Message[] mails = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

            for(Message mail : mails) {
                mail.setFlag(Flags.Flag.SEEN, true);

                if(Pattern.matches(subjectRegex, mail.getSubject())) {
                    InternetAddress sender = (InternetAddress) mail.getFrom()[0];
                    String senderMail = sender.getAddress();
                    String pathToCV = getAttachment(mail);
                    if(pathToCV != null)
                        applicants.add(new Applicant(senderMail, pathToCV));
                }
                else
                    mail.setFlag(Flags.Flag.DELETED, true);
            }

            inbox.close(true);
            return applicants;
        }
        catch (MessagingException e){
            System.out.println("Error: Couldn't connect to mail server via IMAP.");
            System.out.println(e.getMessage());
        }
        return null;
    }

    // Method sends a response email to given applicant
    public void sendResponse(Applicant applicant, boolean accepted) {
        try {
            Session session = establishSmtpConnection();
            Message message = new MimeMessage(session);

            message.setFrom(new InternetAddress(accountMail));
            message.setSubject("Hogwart Rekrutacja - Wynik pierwszego etapu.");
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(applicant.getEmail()));

            message.setText(createResponseText(accepted));
            Transport.send(message);
        }
        catch (MessagingException e) {
            System.out.println("Error: Couldn't connect to mail server via SMTP.");
            System.out.println(e.getMessage());
        }
    }

    // Method checks if given folder name is already in use on the drive
    private String findFolderId(String dirName){
        try{
            String query = "name='" + dirName + "' and mimeType='application/vnd.google-apps.folder'"
                    + " and '" + rootFolderId + "' in parents and trashed=false";

            FileList result = drive.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("files(id)")
                    .execute();
            List<com.google.api.services.drive.model.File> files = result.getFiles();

            if(files != null && !files.isEmpty())
                return files.get(0).getId();
        }
        catch (IOException e){
            System.out.println("Error: Couldn't access Google Drive!!!");
        }
        return null;
    }

    // Method acts as a simple folder name generator to avoid collisions on the drive
    private String generateFolderName(String folderName){
        String result = folderName;
        int count = 0;

        while(findFolderId(result) != null){
            count++;
            result = folderName + "-" +  count;
        }

        return result;
    }

    private String createFolder(String folderName) throws IOException {
        try {
            String name = generateFolderName(folderName);
            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();

            fileMetadata.setName(name);
            fileMetadata.setMimeType("application/vnd.google-apps.folder");
            fileMetadata.setParents(Collections.singletonList(rootFolderId));

            com.google.api.services.drive.model.File folder = drive.files().create(fileMetadata)
                    .setFields("id")
                    .execute();

            return folder.getId();
        }
        catch (IOException e){
            System.out.println("Error: Couldn't create folder on Google Drive!!!");
            System.out.println(e.getMessage());
        }
        return null;
    }

    private void uploadFile(String folderId, String filePath) throws IOException {
        Path path = Paths.get(filePath);
        File file = path.toFile();

        com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
        fileMetadata.setParents(Collections.singletonList(folderId));
        fileMetadata.setName(file.getName());

        String mimeType = Files.probeContentType(path);
        if(mimeType == null)
            mimeType = "application/octet-stream"; // fallback mimeType

        FileContent content = new FileContent(mimeType, file);

        com.google.api.services.drive.model.File uploadedFile = drive.files().create(fileMetadata, content)
                .setFields("id, webViewLink, parents")
                .execute();
    }

    //Method sends file under given path to google drive cloud storage
    public void sendFileToCloud(String folderName, String pathToFile){
        try{
            String folderId = createFolder(folderName);
            if(folderId != null)
                uploadFile(folderId, pathToFile);
        }
        catch (IOException e){
            System.out.println("Error: Couldn't create folder or file on the drive!!!");
            System.out.println(e.getMessage());
        }
    }
}
