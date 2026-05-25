# Memes Telegram Bot (Java + SQLite + Docker)

Телеграм-бот, который объясняет мемы, сленг и интернет-фразы через `kie.ai` (`Gemini 3 Flash`).

## Что умеет
- Отправляет приветствие в формате чата по команде `/start`
- Принимает сообщение пользователя и объясняет мем/сленг
- Если запрос не по теме мемов/сленга, отвечает в молодежном стиле, что это не его специализация
- Обрабатывает только одно сообщение на чат одновременно
- Пока идет AI-обработка, новые сообщения этого чата игнорируются
- Через 1 секунду после ответа отправляет follow-up с предложением спросить еще
- Логирует запросы и ответы в SQLite

## Как создавался бот
1. Создан Maven-проект с Java 17 и точкой входа `com.memesbot.App`.
2. Подключены зависимости в `pom.xml`: TelegramBots API, SQLite JDBC, Jackson, SLF4J.
3. Добавлен конфиг `BotConfig`, который читает переменные окружения и подставляет дефолты.
4. Реализован SQLite-репозиторий `RequestLogRepository` для хранения истории запросов и ответов.
5. Реализован AI-клиент `KieAiClient` для запроса в `https://api.kie.ai/gemini-3-flash/v1/chat/completions`.
6. Добавлен сервис `MemeAssistantService` с бизнес-логикой маршрутизации запроса.
7. Добавлена локальная эвристика `SlangHeuristic`, чтобы отсеивать явно нецелевые сообщения.
8. Реализован Telegram-бот `MemesTelegramBot` с long polling и защитой от параллельной обработки через `busyChats`.
9. Добавлена контейнеризация: `Dockerfile`, `.dockerignore`, volume для SQLite-файла.
10. Добавлен `README` и `.env.example` для быстрого запуска.

## Поток обработки сообщения
1. Telegram присылает `Update` в `MemesTelegramBot.onUpdateReceived(...)`.
2. Если это `/start`, бот отправляет приветствие и завершает обработку.
3. Для обычного текста бот пытается поставить `chatId` в `busyChats`.
4. Если чат уже занят, сообщение игнорируется.
5. Если чат свободен, задача уходит в `workerPool` и вызывается `handleUserRequest(...)`.
6. `MemeAssistantService.handle(...)` делает эвристическую проверку и при необходимости вызывает `KieAiClient.explain(...)`.
7. Ответ и метаданные сохраняются в SQLite через `RequestLogRepository.log(...)`.
8. Бот отправляет основной ответ, ждет 1 секунду и отправляет follow-up.
9. В `finally` чат удаляется из `busyChats`, и можно обрабатывать следующее сообщение.

## Классы и функции

### `App`
- `main(String[] args)`: точка входа, собирает все компоненты, инициализирует БД, регистрирует бота в Telegram API и удерживает процесс живым.

### `BotConfig`
- `fromEnv()`: читает переменные окружения, валидирует обязательные поля и подставляет дефолтные URL/пути.
- `require(Map<String, String> env, String key)`: проверяет обязательную переменную окружения.

### `MemesTelegramBot`
- `onUpdateReceived(Update update)`: фильтрует апдейты, обрабатывает `/start`, включает защиту от параллельных запросов на чат.
- `handleUserRequest(long chatId, long userId, String username, String userText)`: вызывает сервис, отправляет ответ, затем follow-up через 1 секунду.
- `resolveUsername(Message message)`: безопасно получает username пользователя (или fallback).
- `sendText(long chatId, String text)`: отправляет текст в Telegram через `execute(...)`.
- `shutdown()`: завершает пул потоков при остановке приложения.

### `MemeAssistantService`
- `handle(long chatId, long userId, String username, String text)`: главная бизнес-логика, решает идти в AI или сразу вернуть fallback-ответ.
- `saveAndBuild(...)`: пишет лог в SQLite и собирает `ResponseDraft`.
- `normalizeUnsupportedAnswer(String aiAnswer)`: страхует ответ при пустом/битом ответе модели.

### `SlangHeuristic`
- `isPotentialMemeOrSlang(String text)`: быстрая эвристика на тему мемов/сленга по длине, ключевым словам и форме вопроса.
- `containsHint(String text)`: проверяет наличие сленг-маркеров.

### `KieAiClient`
- `explain(String userText)`: отправляет запрос в `kie.ai`, обрабатывает HTTP-ошибки и fallback-сообщения.
- `buildRequestBody(String userText)`: собирает JSON тела запроса с system prompt и `response_format: json_object`.
- `parseModelResponse(String rawResponse)`: извлекает контент из `choices[0].message.content`, валидирует и преобразует в `AiReply`.
- `parseEmbeddedJson(String content)`: достает JSON даже если модель добавила лишний текст вокруг объекта.
- `extractContentText(JsonNode contentNode)`: нормализует форматы `content` (строка/массив).

### `RequestLogRepository`
- `init()`: создает таблицу `requests`, если ее нет.
- `log(...)`: сохраняет факт запроса и ответа.
- `ensureSqliteFolderExists()`: создает директорию для `.db` файла.

### Модели
- `AiReply(boolean supportedTopic, String answer)`: результат от AI-клиента.
- `ResponseDraft(String primaryReply, String followupReply, boolean supportedTopic)`: готовый пакет сообщений для отправки пользователю.

## Структура таблицы SQLite
Таблица `requests`:
- `id`: автоинкрементный первичный ключ
- `chat_id`: идентификатор чата Telegram
- `user_id`: идентификатор пользователя Telegram
- `username`: username или fallback имя
- `user_text`: исходный текст пользователя
- `bot_text`: ответ бота
- `supported_topic`: `1` если тема поддержана, иначе `0`
- `created_at`: время записи

## Переменные окружения
- `TELEGRAM_BOT_TOKEN` (обязательно)
- `TELEGRAM_BOT_USERNAME` (обязательно)
- `KIE_API_KEY` (обязательно)
- `KIE_API_URL` (опционально, по умолчанию `https://api.kie.ai/gemini-3-flash/v1/chat/completions`)
- `SQLITE_JDBC_URL` (опционально, по умолчанию `jdbc:sqlite:data/memesbot.db`)

## Локальный запуск
```bash
mvn clean package

TELEGRAM_BOT_TOKEN=... \
TELEGRAM_BOT_USERNAME=... \
KIE_API_KEY=... \
java -jar target/memes-bot-1.0.0.jar
```

## Docker
```bash
docker build -t memes-bot .

docker run -d \
  --name memes-bot \
  -e TELEGRAM_BOT_TOKEN=... \
  -e TELEGRAM_BOT_USERNAME=... \
  -e KIE_API_KEY=... \
  -v $(pwd)/data:/app/data \
  memes-bot
```
