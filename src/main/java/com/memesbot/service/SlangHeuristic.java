package com.memesbot.service;

import java.util.Set;

public class SlangHeuristic {
    private static final Set<String> QUESTION_STARTERS = Set.of(
            "кто", "что", "где", "когда", "зачем", "почему", "как", "сколько", "какой", "какая", "какие"
    );

    private static final Set<String> SLANG_HINTS = Set.of(
            "кринж", "краш", "рофл", "имба", "скуф", "шиппер", "вайб", "сигма", "база", "флекс",
            "форс", "мем", "пранк", "чилл", "зумер", "душнила", "чек", "зашквар", "пикми", "кек",
            "лол", "омг", "изи", "жиза", "хайп", "канон", "редфлаг", "гринфлаг", "фомо", "делулу"
    );

    public boolean isPotentialMemeOrSlang(String text) {
        if (text == null) {
            return false;
        }

        String normalized = text.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return false;
        }

        int words = normalized.split("\\s+").length;
        if (words <= 5) {
            return true;
        }

        if (containsHint(normalized)) {
            return true;
        }

        if (words > 14) {
            return false;
        }

        String firstWord = normalized.split("\\s+")[0].replaceAll("[^а-яa-z0-9]", "");
        if (QUESTION_STARTERS.contains(firstWord) && normalized.endsWith("?")) {
            return false;
        }

        return normalized.length() <= 90;
    }

    private boolean containsHint(String text) {
        for (String hint : SLANG_HINTS) {
            if (text.contains(hint)) {
                return true;
            }
        }
        return false;
    }
}
