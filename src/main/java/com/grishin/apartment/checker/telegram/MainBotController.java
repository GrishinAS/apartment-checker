package com.grishin.apartment.checker.telegram;

import com.grishin.apartment.checker.config.ApartmentsConfig;
import com.grishin.apartment.checker.config.CommunityConfig;
import com.grishin.apartment.checker.dto.ApartmentFilter;
import com.grishin.apartment.checker.service.UserFilterService;
import com.grishin.apartment.checker.storage.UnitAmenityRepository;
import com.grishin.apartment.checker.storage.entity.UnitAmenity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

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
                    userStates.put(chatId, ConversationState.SETTING_MAX_PRICE);
                    sendPriceRequest(chatId);
                    break;
                case SETTING_MAX_PRICE:
                    processPrice(chatId, messageText);
                    userStates.put(chatId, ConversationState.SETTING_MIN_DATE);
                    sendDateRangeSlider(chatId, new Date());
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
            int messageId = update.getCallbackQuery().getMessage().getMessageId();

            String[] parts = callbackData.split(":");
            String action = parts[0];

            ApartmentFilter preferences = userPreferences.get(chatId);

            switch (action) {

                case "min_date":
                    handleMinDateCallback(new Date(Long.parseLong(parts[1])), preferences, chatId, messageId);
                    break;

                case "max_date":
                    handleMaxDateCallback(new Date(Long.parseLong(parts[1])), preferences, chatId, messageId);
                    break;
            }
        } catch (NumberFormatException | TelegramApiException e) {
            log.error("Error handling callback query", e);
            throw new RuntimeException(e);
        }
    }

    private void handleMaxDateCallback(Date dateValue, ApartmentFilter preferences, long chatId, int messageId) throws TelegramApiException {
        preferences.setMaxDate(dateValue);
        if (dateValue == preferences.getMinDate()) {
            // If max equals min, just proceed to summary
            showPreferencesSummary(chatId);
            userStates.put(chatId, ConversationState.REVIEW_PREFERENCES);
        } else {
            EditMessageText editMessage = createDateSliderMessage(chatId, messageId, dateValue, "max");
            execute(editMessage);

            showPreferencesSummary(chatId);
            userStates.put(chatId, ConversationState.REVIEW_PREFERENCES);
        }
    }

    private void handleMinDateCallback(Date dateValue, ApartmentFilter preferences, long chatId, int messageId) throws TelegramApiException {
        preferences.setMinDate(dateValue);
        // Skip max day selection if selected date is the maximum possible date
        Date maxPossibleDate = getDateWithOffset(90);
        if (dateValue.equals(maxPossibleDate)) {
            preferences.setMaxDate(maxPossibleDate);
            showPreferencesSummary(chatId);
            userStates.put(chatId, ConversationState.REVIEW_PREFERENCES);
        } else {
            EditMessageText editMessage = createDateSliderMessage(chatId, messageId, dateValue, "min");
            execute(editMessage);

            Date initialMaxDate = preferences.getMaxDate();
            if (initialMaxDate == null || initialMaxDate.before(dateValue)) {
                initialMaxDate = dateValue;
            }
            sendDateRangeSlider(chatId, initialMaxDate);
            userStates.put(chatId, ConversationState.SETTING_MAX_DATE);
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
        if (value <= MAX_PRICE && value >= MIN_PRICE) {
            if (state == ConversationState.SETTING_MIN_PRICE)
                preferences.setMinPrice(value);
            else if (state == ConversationState.SETTING_MAX_PRICE)
                preferences.setMaxPrice(value);
            else throw new RuntimeException("Invalid state");
        } else  {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText("Entered price should be between %d and %d".formatted(MIN_PRICE, MAX_PRICE));
            sendPriceRequest(chatId);
        }
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

    public void sendDateRangeSlider(long chatId, Date currentDate) throws TelegramApiException {
        String title = userStates.get(chatId) == ConversationState.SETTING_MIN_DATE ?
                "Set minimum date:" : "Set maximum date:";

        Date date = Calendar.getInstance().getTime();
        String formattedDate = formatDate(date);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(title + " " + formattedDate + "\n\n" + createDateSliderView(currentDate));
        message.setReplyMarkup(createDateSliderKeyboard(currentDate,
                userStates.get(chatId) == ConversationState.SETTING_MIN_DATE ? "min_date" : "max_date"));

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

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(summaryMessage.toString());

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
