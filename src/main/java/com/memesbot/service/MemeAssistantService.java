package com.memesbot.service;

import com.memesbot.ai.KieAiClient;
import com.memesbot.db.RequestLogRepository;
import com.memesbot.model.AiReply;
import com.memesbot.model.ResponseDraft;

public class MemeAssistantService {
    private static final String UNSUPPORTED_FALLBACK = "Йо, это не мой профиль. Я разбираю только мемы, сленг и интернет-фразы.";
    private static final String FOLLOWUP_TEXT = "Если хочешь, закидывай еще мем или сленг, и я быстро расшифрую.";

    private final KieAiClient kieAiClient;
    private final RequestLogRepository requestLogRepository;
    private final SlangHeuristic slangHeuristic;

    public MemeAssistantService(KieAiClient kieAiClient,
                                RequestLogRepository requestLogRepository,
                                SlangHeuristic slangHeuristic) {
        this.kieAiClient = kieAiClient;
        this.requestLogRepository = requestLogRepository;
        this.slangHeuristic = slangHeuristic;
    }

    public ResponseDraft handle(long chatId, long userId, String username, String text) {
        String cleanText = text == null ? "" : text.trim();
        if (cleanText.isEmpty()) {
            return saveAndBuild(chatId, userId, username, cleanText, UNSUPPORTED_FALLBACK, false);
        }

        if (!slangHeuristic.isPotentialMemeOrSlang(cleanText)) {
            return saveAndBuild(chatId, userId, username, cleanText, UNSUPPORTED_FALLBACK, false);
        }

        AiReply aiReply = kieAiClient.explain(cleanText);
        if (!aiReply.supportedTopic()) {
            String answer = normalizeUnsupportedAnswer(aiReply.answer());
            return saveAndBuild(chatId, userId, username, cleanText, answer, false);
        }

        return saveAndBuild(chatId, userId, username, cleanText, aiReply.answer(), true);
    }

    private ResponseDraft saveAndBuild(long chatId,
                                       long userId,
                                       String username,
                                       String userText,
                                       String primaryReply,
                                       boolean supportedTopic) {
        requestLogRepository.log(chatId, userId, username, userText, primaryReply, supportedTopic);
        return new ResponseDraft(primaryReply, FOLLOWUP_TEXT, supportedTopic);
    }

    private String normalizeUnsupportedAnswer(String aiAnswer) {
        if (aiAnswer == null || aiAnswer.isBlank()) {
            return UNSUPPORTED_FALLBACK;
        }
        return aiAnswer.trim();
    }
}
