package pl.hogwart.cvprocessor.services;

import jakarta.mail.search.FlagTerm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.hogwart.cvprocessor.model.Applicant;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.List;
import java.util.Properties;

/*
    Service handles all action related to google account or google api.
 */

@Service
public class CloudService {
    //Credentials to google account
    @Value("${google.account.app-password}")
    private static String password;

    @Value("${google.account.mail}")
    private static String mail;

    // Method establishes connection to the email server
    private static Store establishConnection() throws MessagingException {
        Properties props = System.getProperties();
        props.setProperty("mail.store.protocol", "+imaps");

        Session session = Session.getDefaultInstance(props, null);
        Store store = session.getStore("+imaps");
        store.connect("imap.googlemail.com", mail, password);
        return store;
    }

    // Method retrieves CVs from email via IMAP
    public List<Applicant> getCVs() {
        try (Store store = establishConnection()) {
            Folder inbox = store.getFolder("inbox");
            inbox.open(Folder.READ_WRITE);
            Message[] mails = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

        }
        catch (MessagingException e){
            System.out.println("Error: Couldn't connect to mail server.");
            System.out.println(e.getMessage());
        }

        return null;
    }

    // Method sends a response email to given applicant
    public void sendResponse(Applicant applicant, boolean accepted) {

    }

    //Method sends file under given path to google drive cloud storage
    public void sendFileToCloud(String pathToFile){

    }
}
