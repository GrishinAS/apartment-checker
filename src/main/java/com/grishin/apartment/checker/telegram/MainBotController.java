package com.grishin.apartment.checker.telegram;

import com.grishin.apartment.checker.config.ApartmentsConfig;
import com.grishin.apartment.checker.config.CommunityConfig;
import com.grishin.apartment.checker.dto.ApartmentFilter;
import com.grishin.apartment.checker.service.UserFilterService;
import com.grishin.apartment.checker.storage.UnitAmenityRepository;
import com.grishin.apartment.checker.storage.entity.UnitAmenity;
import io.github.dostonhamrakulov.InlineCalendarBuilder;
import io.github.dostonhamrakulov.InlineCalendarCommandUtil;
import io.github.dostonhamrakulov.LanguageEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static com.grishin.apartment.checker.telegram.KeyboardUtils.*;
import static java.util.stream.Collectors.toCollection;

@Slf4j
@Service
public class MainBotController extends TelegramLongPollingBot {
    private final Map<Long, ConversationState> userStates = new HashMap<>();
    private final Map<Long, ApartmentFilter> userPreferences = new HashMap<>();
    private final Map<Long, String> selectedCommunities = new HashMap<>();
    private final ApartmentsConfig apartmentsConfig;
    private final UserFilterService userFilterService;
    private final UnitAmenityRepository unitAmenityRepository;

    InlineCalendarBuilder inlineCalendarBuilder = new InlineCalendarBuilder(LanguageEnum.EN);

    @Value("${telegram.bot.name}")
    private String botName;

    public MainBotController(
            @Value("${telegram.bot.token}") String token,
            ApartmentsConfig apartmentsConfig,
            UserFilterService userFilterService,
            UnitAmenityRepository unitAmenityRepository) {
        super(token);
        this.apartmentsConfig = apartmentsConfig;
        this.userFilterService = userFilterService;
        this.unitAmenityRepository = unitAmenityRepository;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallbackQuery(update);
                return;
            }
            if (!update.hasMessage() || !update.getMessage().hasText()) {
                return;
            }

            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            if (!userStates.containsKey(chatId)) {
                userStates.put(chatId, ConversationState.IDLE);
            }

            ConversationState currentState = userStates.get(chatId);
            log.info("Received message '{}' in state '{}'", messageText, currentState);
            switch (currentState) {
                case IDLE:
                    if (messageText.equals("/start")) {
                        sendCommunitySelection(chatId);
                        userStates.put(chatId, ConversationState.WAITING_FOR_COMMUNITY);
                        userPreferences.put(chatId, new ApartmentFilter());
                    }
                    break;

                case WAITING_FOR_COMMUNITY:
                    processSelectedCommunity(chatId, messageText);
                    sendAmenitiesOptions(chatId);
                    userStates.put(chatId, ConversationState.WAITING_FOR_AMENITIES);
                    break;

                case WAITING_FOR_AMENITIES:
                    processSelectedAmenities(chatId, messageText);
                    userStates.put(chatId, ConversationState.SETTING_MIN_PRICE);
                    sendPriceRequest(chatId);
                    break;

                case SETTING_MIN_PRICE:
                    processPrice(chatId, messageText);
                    sendPriceRequest(chatId);
                    break;
                case SETTING_MAX_PRICE:
                    processPrice(chatId, messageText);
                    sendDateRangeSlider(chatId, update);
                    break;

                case REVIEW_PREFERENCES:
                    if (messageText.equalsIgnoreCase("confirm")) {
                        saveUserPreferences(chatId);
                        sendFinalConfirmation(chatId);
                        userStates.put(chatId, ConversationState.IDLE);
                    } else if (messageText.equalsIgnoreCase("restart")) {
                        sendCommunitySelection(chatId);
                        userStates.put(chatId, ConversationState.WAITING_FOR_COMMUNITY);
                        userPreferences.put(chatId, new ApartmentFilter());
                    }
                    break;
            }
        } catch (Exception e) {
            log.error("Error during handling update", e);
        }
    }

    private void handleCallbackQuery(Update update) {
        try {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            String[] parts = callbackData.split(":");

            if (!parts[1].equals("DATE")) {
                if (InlineCalendarCommandUtil.isCalendarIgnoreButtonClicked(update)) {
                    return;
                }
                if (InlineCalendarCommandUtil.isCalendarNavigationButtonClicked(update)) {
                    SendMessage sendMessage = new SendMessage();
                    sendMessage.setChatId(String.valueOf(chatId));
                    if (update.getCallbackQuery().getMessage() instanceof Message message)
                        sendMessage.setText(message.getText());
                    else
                        sendMessage.setText("");
                    sendMessage.setReplyMarkup(inlineCalendarBuilder.build(update));
                    execute(sendMessage);
                    return;
                }
            }
            Date chosenDate = DATE_FORMAT.parse(parts[2]);

            ConversationState state = userStates.get(chatId);
            log.info("Received callback query with data '{}', state: {}", callbackData, state);
            switch (state) {
                case SETTING_MIN_DATE:
                    handleMinDateCallback(chosenDate, chatId, update);
                    break;

                case SETTING_MAX_DATE:
                    handleMaxDateCallback(chosenDate, chatId, update);
                    break;
            }
        } catch (NumberFormatException | TelegramApiException | ParseException e) {
            log.error("Error handling callback query", e);
            throw new RuntimeException(e);
        }
    }

    private void handleMaxDateCallback(Date dateValue, long chatId, Update update) throws TelegramApiException {
        ApartmentFilter preferences = userPreferences.get(chatId);
        LocalDate enteredDate = dateValue.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        Date savedMinDate = preferences.getMinDate();
        LocalDate minDate = savedMinDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDate maxPossibleDate = LocalDate.now().plusDays(90);
        if (enteredDate.isAfter(minDate) && enteredDate.isBefore(maxPossibleDate)) {
            log.info("Setting max date to {}", dateValue);
            preferences.setMaxDate(dateValue);
            userStates.put(chatId, ConversationState.REVIEW_PREFERENCES);
            showPreferencesSummary(chatId);
        } else {
            log.warn("Incorrect max date chosen: {}, another try", dateValue);
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText("Entered date should be between minimum date and three months from today");
            execute(message);
            sendDateRangeSlider(chatId, update);
        }
    }

    private void handleMinDateCallback(Date dateValue, long chatId, Update update) throws TelegramApiException {
        ApartmentFilter preferences = userPreferences.get(chatId);
        LocalDate enteredDate = dateValue.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDate today = LocalDate.now();
        LocalDate maxPossibleDate = today.plusDays(90);
        if (enteredDate.isAfter(today) && enteredDate.isBefore(maxPossibleDate)) {
            log.info("Setting min date to {}", dateValue);
            preferences.setMinDate(dateValue);
            userStates.put(chatId, ConversationState.SETTING_MAX_DATE);
            sendDateRangeSlider(chatId, update);
        } else {
            log.warn("Incorrect min date chosen: {}, another try", dateValue);
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText("Entered date should be between today and three months from today");
            execute(message);
            sendDateRangeSlider(chatId, update);
        }
    }

    private void sendPriceRequest(long chatId) throws TelegramApiException {
        ConversationState state = userStates.get(chatId);
        log.info("Sending price request to chatId, state {}: {}", chatId, state);
        String title;
        if (state == ConversationState.SETTING_MIN_PRICE)
            title = "Set minimum price:";
        else if (state == ConversationState.SETTING_MAX_PRICE)
            title = "Set maximum price:";
        else throw new RuntimeException("Invalid state");
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(title);

        execute(message);
    }

    private void processPrice(long chatId, String priceString) throws TelegramApiException {
        int value = Integer.parseInt(priceString);
        ConversationState state = userStates.get(chatId);
        log.info("Processing price: {}, state: {}", value, state);
        ApartmentFilter preferences = userPreferences.get(chatId);
        if (value > MAX_PRICE || value < MIN_PRICE) {
            log.warn("Incorrect price chosen: {}, another try", priceString);
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText("Entered price should be between %d and %d".formatted(MIN_PRICE, MAX_PRICE));
            execute(message);
            sendPriceRequest(chatId);
            return;
        }
        if (state == ConversationState.SETTING_MIN_PRICE) {
            log.info("Setting min price to {}", value);
            preferences.setMinPrice(value);
            userStates.put(chatId, ConversationState.SETTING_MAX_PRICE);
        }
        else if (state == ConversationState.SETTING_MAX_PRICE) {
            log.info("Setting max price to {}", value);
            preferences.setMaxPrice(value);
            userStates.put(chatId, ConversationState.SETTING_MIN_DATE);
        }
        else throw new RuntimeException("Invalid state");
    }

    private void sendCommunitySelection(long chatId) throws TelegramApiException {
        SendMessage message = createMessageWithKeyboard(
                chatId,
                "Please select your preferred community:",
                createKeyboardFromList(apartmentsConfig.getCommunities().stream().map(CommunityConfig::getName).toList())
        );
        execute(message);
    }

    private void processSelectedCommunity(long chatId, String selectedCommunity) {
        selectedCommunities.put(chatId, selectedCommunity);
    }

    private void sendAmenitiesOptions(long chatId) throws TelegramApiException {
        List<String> amenities = unitAmenityRepository.findAll().stream()
                .map(UnitAmenity::getAmenityName).collect(toCollection(ArrayList::new));
        SendMessage message = createMessageWithKeyboard(
                chatId,
                "Now, please select your desired amenities (comma-separated if multiple):",
                createKeyboardFromList(amenities)
        );
        execute(message);
    }

    public void sendDateRangeSlider(long chatId, Update update) throws TelegramApiException {
        ConversationState state = userStates.get(chatId);
        String title;
        if (state == ConversationState.SETTING_MIN_DATE)
            title = "Set minimum date:";
        else if (state == ConversationState.SETTING_MAX_DATE)
            title = "Set maximum date:";
        else throw new RuntimeException("Invalid state");

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(title);
        message.setReplyMarkup(inlineCalendarBuilder.build(update));

        execute(message);
    }

    private void showPreferencesSummary(long chatId) throws TelegramApiException {
        ApartmentFilter prefs = userPreferences.get(chatId);
        String selectedCommunity = selectedCommunities.get(chatId);

        StringBuilder summaryMessage = new StringBuilder();
        summaryMessage.append("⭐ Your Preferences Summary ⭐\n\n");
        summaryMessage.append("🏠 Community: ").append(selectedCommunity).append("\n\n");

        summaryMessage.append("🏷️ Filters: ").append(prefs.getAmenities()).append("\n\n");

        summaryMessage.append("💰 Price Range: ").append(prefs.getMinPrice()).append(" - ").append(prefs.getMaxPrice()).append(" ₽\n\n");

        Date minDate = Calendar.getInstance().getTime();
        Date maxDate = Calendar.getInstance().getTime();
        summaryMessage.append("📅 Date Range: ").append(formatDate(minDate)).append(" - ").append(formatDate(maxDate)).append("\n\n");

        summaryMessage.append("Is this correct? Type 'confirm' to save or 'restart' to begin again.");
        SendMessage message = createMessageWithKeyboard(
                chatId,
                "Is this correct? Press 'confirm' to save or 'restart' to begin again.",
                createKeyboardFromList(List.of("Confirm", "Restart"))
        );
        execute(message);
    }

    private void processSelectedAmenities(long chatId, String amenitiesText) {
        List<String> selectedAmenities = Arrays.asList(amenitiesText.split(",\\s*"));
        userPreferences.get(chatId).setAmenities(selectedAmenities);
    }

    private void sendFinalConfirmation(long chatId) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("✅ Your preferences have been saved! You will receive updates based on your criteria.\n\nType /start to set new preferences anytime.");

        execute(message);
    }

    private void saveUserPreferences(long chatId) {
        ApartmentFilter gatheredPreferences = userPreferences.get(chatId);
        String selectedCommunity = selectedCommunities.get(chatId);
        userFilterService.saveUserFilters(chatId, selectedCommunity, gatheredPreferences);
    }


    @Override
    public String getBotUsername() {
        return botName;
    }

}
