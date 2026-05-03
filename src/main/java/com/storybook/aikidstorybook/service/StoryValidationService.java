package com.storybook.aikidstorybook.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StoryValidationService {

    private static final Set<String> BANNED_WORDS = new HashSet<>(Arrays.asList(
            "kill", "die", "dead", "blood", "scary", "hate", "angry", "stupid", "demon", "monster"
    ));

    private static final Map<String, String> COMMON_FIXES = Map.of(
            " teh ", " the ",
            " adn ", " and ",
            " dont ", " don't ",
            " doesnt ", " doesn't ",
            " isnt ", " isn't ",
            " alot ", " a lot ",
            "  ", " "
    );

    public String filterUnsafeWords(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String safeText = text;
        for (String banned : BANNED_WORDS) {
            String lower = banned.toLowerCase();
            safeText = safeText.replaceAll("(?i)\\b" + lower + "\\b", "gentle");
        }
        return safeText;
    }

    public String correctCommonGrammar(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String corrected = text.trim();
        for (Map.Entry<String, String> entry : COMMON_FIXES.entrySet()) {
            corrected = corrected.replace(entry.getKey(), entry.getValue());
        }

        corrected = corrected.replaceAll("\\s+([,.!?])", "$1");
        corrected = corrected.replaceAll("\\s{2,}", " ");
        corrected = capitalizeSentences(corrected);

        return corrected;
    }

    public List<String> validateReadability(List<String> pages, String ageGroup) {
        List<String> warnings = new ArrayList<>();
        double maxGrade = mapAgeGroupToMaxGrade(ageGroup);

        for (int i = 0; i < pages.size(); i++) {
            String page = pages.get(i);
            if (page == null || page.isBlank()) {
                warnings.add("Page " + (i + 1) + " is empty.");
                continue;
            }

            int words = countWords(page);
            int sentences = Math.max(1, countSentences(page));
            int syllables = countSyllables(page);
            double grade = computeFleschKincaid(words, sentences, syllables);

            if (grade > maxGrade + 1.5) {
                warnings.add("Page " + (i + 1) + " may be too advanced for " + ageGroup + ".");
            }
            if (words > 35) {
                warnings.add("Page " + (i + 1) + " is a bit long; try shorter sentences for young readers.");
            }
        }

        return warnings;
    }

    private double computeFleschKincaid(int words, int sentences, int syllables) {
        if (words == 0 || sentences == 0) {
            return 0.0;
        }
        return 0.39 * ((double) words / sentences) + 11.8 * ((double) syllables / words) - 15.59;
    }

    private int countWords(String text) {
        return (int) Arrays.stream(text.split("\\s+"))
                .filter(word -> !word.isBlank())
                .count();
    }

    private int countSentences(String text) {
        return (int) Arrays.stream(text.split("[.!?]+"))
                .filter(sentence -> !sentence.isBlank())
                .count();
    }

    private int countSyllables(String text) {
        int count = 0;
        for (String word : text.split("\\s+")) {
            if (!word.isBlank()) {
                count += countSyllablesInWord(word);
            }
        }
        return Math.max(1, count);
    }

    private int countSyllablesInWord(String word) {
        String normalized = word.toLowerCase().replaceAll("[^a-z]", "");
        if (normalized.isEmpty()) {
            return 0;
        }

        int vowels = 0;
        boolean lastWasVowel = false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            boolean isVowel = "aeiouy".indexOf(c) >= 0;
            if (isVowel && !lastWasVowel) {
                vowels++;
            }
            lastWasVowel = isVowel;
        }

        if (normalized.endsWith("e") && vowels > 1) {
            vowels--;
        }

        return Math.max(vowels, 1);
    }

    private double mapAgeGroupToMaxGrade(String ageGroup) {
        if (ageGroup == null) {
            return 3.0;
        }
        return switch (ageGroup) {
            case "3-5" -> 1.5;
            case "5-7" -> 2.5;
            case "7-9" -> 4.0;
            case "9-12" -> 6.0;
            default -> 3.0;
        };
    }

    private String capitalizeSentences(String text) {
        StringBuilder result = new StringBuilder();
        boolean capitalize = true;
        for (char c : text.toCharArray()) {
            if (capitalize && Character.isLetter(c)) {
                result.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                result.append(c);
            }
            if (c == '.' || c == '!' || c == '?') {
                capitalize = true;
            }
        }
        return result.toString();
    }
}
