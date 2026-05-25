package com.memesbot;

import com.memesbot.ai.KieAiClient;
import com.memesbot.bot.MemesTelegramBot;
import com.memesbot.config.BotConfig;
import com.memesbot.db.RequestLogRepository;
import com.memesbot.service.MemeAssistantService;
import com.memesbot.service.SlangHeuristic;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.concurrent.CountDownLatch;

public class App {
    public static void main(String[] args) {
        try {
            BotConfig config = BotConfig.fromEnv();

            RequestLogRepository logRepository = new RequestLogRepository(config.sqliteJdbcUrl());
            logRepository.init();

            KieAiClient kieAiClient = new KieAiClient(config.kieApiUrl(), config.kieApiKey());
            MemeAssistantService memeAssistantService = new MemeAssistantService(
                    kieAiClient,
                    logRepository,
                    new SlangHeuristic()
            );

            MemesTelegramBot bot = new MemesTelegramBot(
                    config.telegramToken(),
                    config.telegramUsername(),
                    memeAssistantService
            );

            Runtime.getRuntime().addShutdownHook(new Thread(bot::shutdown));

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);

            System.out.println("Memes bot started.");
            new CountDownLatch(1).await();
        } catch (Exception e) {
            System.err.println("Failed to start bot: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
