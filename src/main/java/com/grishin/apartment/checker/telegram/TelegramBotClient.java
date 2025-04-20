package com.grishin.apartment.checker.telegram;

import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.function.Consumer;

@Slf4j
public class TelegramBotClient extends TelegramLongPollingBot {

    private final String botName;
    private final Consumer<Update> callback;

    public TelegramBotClient(String botToken, String botName, Consumer<Update> callback) {
        super(botToken);
        this.botName = botName;
        this.callback = callback;
    }

    @Override
    public void onUpdateReceived(Update update) {
        callback.accept(update);
    }

    @Override
    public String getBotUsername() {
        return botName;
    }
}

