package pl.hogwart.cvprocessor.services;

import org.springframework.stereotype.Service;
import pl.hogwart.cvprocessor.model.Candidate;
import pl.hogwart.cvprocessor.model.Position;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SkillsValidatorService {

    private static final String[] teacherRequirements = new String[] {
        "zaklęcia ochronne",
        "zaklęcia obronne",
        "runy",
        "klątwy",
        "uroki",
        "wykrywanie magii",
        "czarna magia",
        "obrona przed istotami magicznymi",
        "pojedynkowanie się",
        "eliksiry ochronne",
        "bariery",
        "nauczanie",
        "historia magii",
        "artefakty magiczne",
        "legilimencja",
        "oklumencja",
        "odporność psychiczna",
        "symulacje magiczne",
        "koordynacja różdżki",
        "zaklęcia lecznicze",
        "współpraca z aururami",
        "iluzje",
        "rytuały",
        "analiza zaklęć",
        "walka magiczna",
        "język wspólny",
        "starożytne zaklęcia",
        "praca z grupą"
    };

    private static final String[] keeperRequirements = new String[] {
        "eliksiry ochronne",
        "uprawa magiczne rośliny",
        "zaklęcia pielęgnacyjne",
        "magiczne rośliny",
        "identyfikacja ingrediencji",
        "pielęgnacja",
        "opieka",
        "oswajanie",
        "magiczne stworzenia",
        "magiczne zwierzęta",
        "smoki",
        "badania terenowe",
        "ekosystem magiczny",
        "identyfikacja roślin",
        "identyfikacja stworzeń",
        "zielarstwo",
        "miksturoznawstwo",
        "anomalie pogodowe",
        "zabezpieczenia leśne",
        "ogrodzenia antymagiczne",
        "ochrona środowiska",
        "leczenie",
        "zbieranie ingrediencji",
        "artefakty naturalne",
        "rozpoznawanie magicznych gatunków",
        "pielęgnacja lasu",
        "praca fizyczna"
    };

    private static String normalize(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.forLanguageTag("pl"));
        return lower;
    }

    private static String[] splitSkillIntoSubparts(String skill) {
        if (skill == null) return new String[0];
        // zamieniamy popularne łączniki na przecinki i splitujemy
        String tmp = skill.replaceAll("(?i)\\s+oraz\\s+", ",")
                .replaceAll("(?i)\\s+i\\s+", ",");
        return Arrays.stream(tmp.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .toArray(String[]::new);
    }

    private static boolean wordContains(String a, String b) {
        // sprawdza, czy w a występuje b jako osobne słowo lub fraza (używając boundary)
        // używamy \b, ale po normalizacji pewne znaki już nie występują
        try {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(b) + "\\b");
            return p.matcher(a).find();
        } catch (Exception e) {
            return a.contains(b);
        }
    }

    // Levenshtein i podobieństwo
    private static int levenshtein(String s1, String s2) {
        int[] prev = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) prev[j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            int[] cur = new int[s2.length() + 1];
            cur[0] = i;
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            prev = cur;
        }
        return prev[s2.length()];
    }

    private static double similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int dist = levenshtein(a, b);
        int max = Math.max(a.length(), b.length());
        return 1.0 - (double) dist / (double) max;
    }

    public static double calculateScoreForPosition(Candidate candidate, Position position){
        List<String> requirements = new ArrayList<String>();
        if(position == Position.TEACHER) requirements =  Arrays.asList(teacherRequirements);
        else if(position == Position.KEEPER)  requirements =  Arrays.asList(keeperRequirements);

        List<String> singleSkills = new ArrayList<>();

        candidate.getSkills().forEach(skill -> {
            String[] subparts = splitSkillIntoSubparts(skill);
            for(String subpart : subparts){
                singleSkills.add(subpart);
            }
        });
        // Normalize list of required skills
        Set<String> candidateSkills = singleSkills.stream()
                .map(SkillsValidatorService::normalize).collect(Collectors.toSet());
        Set<String> requiredSkills = new HashSet<>(requirements);

        double matches = 0;

        for(String skill : candidateSkills){
            for(String requirement : requiredSkills){
                if(wordContains(skill, requirement) || wordContains(requirement, skill)){
                    System.out.println("Req: " + requirement + "; Skill: " + skill);
                    matches++;
                }
            }
        }
        return Math.round(matches / requirements.size() * 100);
    }
}
