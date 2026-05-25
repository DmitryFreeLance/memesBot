package com.memesbot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.memesbot.model.AiReply;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class KieAiClient {
    private static final String SYSTEM_PROMPT = """
            Ты эксперт по мемам, сленгу и интернет-фразам.
            Твоя задача: проверить, относится ли сообщение к мемам/сленгу/интернет-фразам.

            Правила:
            1) Если запрос про мем, сленг, аббревиатуру, интернет-шутку или фразу из онлайн-культуры,
               верни supported_topic=true и кратко объясни значение простым языком на русском.
            2) Если запрос не по теме (обычные вопросы, учеба, техника, политика, математика и т.д.),
               верни supported_topic=false и ответ в молодежном стиле, что это не твоя специализация.
            3) Ответ должен быть безопасным, без токсичности и оскорблений.
            4) Верни ТОЛЬКО JSON в формате:
               {"supported_topic": true, "answer": "..."}
               или
               {"supported_topic": false, "answer": "..."}
            5) Никакого markdown, текста вне JSON, комментариев и префиксов.
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;

    public KieAiClient(String endpoint, String apiKey) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.endpoint = URI.create(endpoint);
        this.apiKey = apiKey;
    }

    public AiReply explain(String userText) {
        try {
            String requestBody = buildRequestBody(userText);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new AiReply(false, "Ой, мемный движ сейчас подвис. Закинь еще разок фразу чуть позже.");
            }

            return parseModelResponse(response.body());
        } catch (Exception e) {
            return new AiReply(false, "Сервак мемологии немного лагает. Попробуй еще раз через минутку.");
        }
    }

    private String buildRequestBody(String userText) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("messages", MAPPER.createArrayNode()
                .add(MAPPER.createObjectNode()
                        .put("role", "system")
                        .put("content", SYSTEM_PROMPT)
                )
                .add(MAPPER.createObjectNode()
                        .put("role", "user")
                        .set("content", MAPPER.createArrayNode()
                                .add(MAPPER.createObjectNode()
                                        .put("type", "text")
                                        .put("text", userText)
                                )
                        )
                )
        );
        root.put("stream", false);
        root.set("response_format", MAPPER.createObjectNode().put("type", "json_object"));

        return MAPPER.writeValueAsString(root);
    }

    private AiReply parseModelResponse(String rawResponse) throws IOException {
        JsonNode root = MAPPER.readTree(rawResponse);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");

        if (contentNode.isMissingNode() || contentNode.isNull()) {
            return new AiReply(false, "Я пока не выкупил запрос. Кинь мем/сленг еще раз, разберем.");
        }

        String content = extractContentText(contentNode);
        if (content.isBlank()) {
            return new AiReply(false, "Я пока не выкупил запрос. Кинь мем/сленг еще раз, разберем.");
        }

        JsonNode answerJson = parseEmbeddedJson(content);

        boolean supported = answerJson.path("supported_topic").asBoolean(false);
        String answer = answerJson.path("answer").asText();

        if (answer == null || answer.isBlank()) {
            return new AiReply(false, "Что-то пусто прилетело. Закидывай мем/сленг, попробуем еще раз.");
        }

        return new AiReply(supported, answer.trim());
    }

    private JsonNode parseEmbeddedJson(String content) throws IOException {
        String trimmed = content.trim();

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return MAPPER.readTree(trimmed);
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return MAPPER.readTree(trimmed.substring(start, end + 1));
        }

        throw new IOException("Model response does not contain JSON");
    }

    private String extractContentText(JsonNode contentNode) {
        if (contentNode.isTextual()) {
            return contentNode.asText("");
        }

        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode node : contentNode) {
                if (node.has("text")) {
                    builder.append(node.path("text").asText(""));
                } else if (node.isTextual()) {
                    builder.append(node.asText(""));
                }
            }
            return builder.toString();
        }

        return contentNode.toString();
    }
}
