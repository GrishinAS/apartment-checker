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
public class BotController extends TelegramLongPollingBot {
    private final Map<Long, ConversationState> userStates = new HashMap<>();
    private final Map<Long, ApartmentFilter> userPreferences = new HashMap<>();
    private final Map<Long, String> selectedCommunities = new HashMap<>();
    private final ApartmentsConfig apartmentsConfig;
    private final UserFilterService userFilterService;
    private final UnitAmenityRepository unitAmenityRepository;

    @Value("${telegram.bot.name}")
    private String botName;

    public BotController(
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
        log.info("Received update: {}", update.toString());
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
                    sendPriceRangeSlider(chatId, MIN_PRICE);
                    userStates.put(chatId, ConversationState.SETTING_MIN_PRICE);
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
                case "min_price":
                    handleMinPriceCallback(Integer.parseInt(parts[1]), preferences, chatId, messageId);
                    break;

                case "max_price":
                    handleMaxPriceCallback(Integer.parseInt(parts[1]), preferences, chatId, messageId);
                    break;

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
            // Update slider UI
            EditMessageText editMessage = createDateSliderMessage(chatId, messageId, dateValue, "min");
            execute(editMessage);

            // Move to max date selection with min date as starting point
            Date initialMaxDate = preferences.getMaxDate();
            if (initialMaxDate == null || initialMaxDate.before(dateValue)) {
                initialMaxDate = dateValue;
            }
            sendDateRangeSlider(chatId, initialMaxDate);
            userStates.put(chatId, ConversationState.SETTING_MAX_DATE);
        }
    }

    private void handleMaxPriceCallback(int value, ApartmentFilter preferences, long chatId, int messageId) throws TelegramApiException {
        if (value >= MIN_PRICE) { // handle incorrect value
            preferences.setMaxPrice(value);
            if (value == MIN_PRICE) {
                // If max equals min, just proceed
                sendDateRangeSlider(chatId, new Date());
                userStates.put(chatId, ConversationState.SETTING_MIN_DATE);
            } else {
                // Update slider and move to date selection
                EditMessageText editMessage = createPriceSliderMessage(chatId, messageId, value, "max");
                execute(editMessage);

                sendDateRangeSlider(chatId, new Date());
                userStates.put(chatId, ConversationState.SETTING_MIN_DATE);
            }
        }
    }

    private void handleMinPriceCallback(int value, ApartmentFilter preferences, long chatId, int messageId) throws TelegramApiException {
        if (value <= MAX_PRICE) { // handle incorrect value
            preferences.setMinPrice(value);
            if (value == MAX_PRICE) {
                // If min price is at max, set max price equal to it
                preferences.setMaxPrice(MAX_PRICE);
                sendDateRangeSlider(chatId, new Date());
                userStates.put(chatId, ConversationState.SETTING_MIN_DATE);
            } else {
                // Update slider and move to max price selection
                EditMessageText editMessage = createPriceSliderMessage(chatId, messageId, value, "min");
                execute(editMessage);

                sendPriceRangeSlider(chatId, preferences.getMinPrice());
                userStates.put(chatId, ConversationState.SETTING_MAX_PRICE);
            }
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

    private void sendPriceRangeSlider(long chatId, int currentValue) throws TelegramApiException {
        String title = userStates.get(chatId) == ConversationState.SETTING_MIN_PRICE ?
                "Set minimum price:" : "Set maximum price:";

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(title + " " + currentValue + " $\n\n" + createPriceSliderView(currentValue));
        message.setReplyMarkup(createPriceSliderKeyboard(currentValue,
                userStates.get(chatId) == ConversationState.SETTING_MIN_PRICE ? "min_price" : "max_price"));

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
