package com.grishin.apartment.checker;

import com.grishin.apartment.checker.telegram.TelegramBot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}


	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Bean
	public TelegramBot telegramBot(
			@Value("APT_BOT_TOKEN") String botToken,
			@Value("APT_CHAT_ID") String chatId) {
		return new TelegramBot(botToken, chatId);
	}
}
