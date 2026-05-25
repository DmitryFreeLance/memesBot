package com.memesbot.bot;

import com.memesbot.model.ResponseDraft;
import com.memesbot.service.MemeAssistantService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MemesTelegramBot extends TelegramLongPollingBot {
    private static final String START_COMMAND = "/start";
    private static final String WELCOME_MESSAGE = """
            Привет! Я бот по мемам и сленгу.

            Пиши любое слово или фразу из интернет-культуры, например:
            - скуф
            - delulu
            - это имба
            - что значит рофл

            Я объясню смысл простыми словами.
            """;

    private final String botUsername;
    private final MemeAssistantService memeAssistantService;
    private final ExecutorService workerPool;
    private final Set<Long> busyChats;

    public MemesTelegramBot(String botToken, String botUsername, MemeAssistantService memeAssistantService) {
        super(botToken);
        this.botUsername = botUsername;
        this.memeAssistantService = memeAssistantService;
        this.workerPool = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
        this.busyChats = ConcurrentHashMap.newKeySet();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update == null || !update.hasMessage()) {
            return;
        }

        Message message = update.getMessage();
        if (!message.hasText()) {
            return;
        }

        long chatId = message.getChatId();
        String text = message.getText().trim();

        if (START_COMMAND.equalsIgnoreCase(text)) {
            if (!busyChats.contains(chatId)) {
                sendText(chatId, WELCOME_MESSAGE);
            }
            return;
        }

        if (!busyChats.add(chatId)) {
            return;
        }

        long userId = message.getFrom() == null ? 0L : message.getFrom().getId();
        String username = resolveUsername(message);

        workerPool.submit(() -> handleUserRequest(chatId, userId, username, text));
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    public void shutdown() {
        workerPool.shutdownNow();
    }

    private void handleUserRequest(long chatId, long userId, String username, String userText) {
        try {
            ResponseDraft draft = memeAssistantService.handle(chatId, userId, username, userText);
            sendText(chatId, draft.primaryReply());

            Thread.sleep(1000);
            sendText(chatId, draft.followupReply());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            sendText(chatId, "Упс, что-то заглючило. Закинь мем еще раз, сейчас починимся.");
        } finally {
            busyChats.remove(chatId);
        }
    }

    private String resolveUsername(Message message) {
        if (message.getFrom() == null) {
            return "unknown";
        }

        if (message.getFrom().getUserName() != null && !message.getFrom().getUserName().isBlank()) {
            return message.getFrom().getUserName();
        }

        String firstName = message.getFrom().getFirstName();
        if (firstName != null && !firstName.isBlank()) {
            return firstName;
        }

        return "user-" + message.getFrom().getId();
    }

    private void sendText(long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(Long.toString(chatId));
        sendMessage.setText(text);
        sendMessage.setDisableWebPagePreview(true);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new IllegalStateException("Failed to send Telegram message", e);
        }
    }
}
