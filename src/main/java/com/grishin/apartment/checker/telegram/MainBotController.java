package com.grishin.apartment.checker.telegram;

import com.grishin.apartment.checker.config.ApartmentsConfig;
import com.grishin.apartment.checker.config.CommunityConfig;
import com.grishin.apartment.checker.dto.ApartmentFilter;
import com.grishin.apartment.checker.dto.UnitMessage;
import com.grishin.apartment.checker.service.UserFilterService;
import com.grishin.apartment.checker.storage.UnitAmenityRepository;
import com.grishin.apartment.checker.storage.entity.Unit;
import com.grishin.apartment.checker.storage.entity.UnitAmenity;
import io.github.dostonhamrakulov.InlineCalendarBuilder;
import io.github.dostonhamrakulov.InlineCalendarCommandUtil;
import io.github.dostonhamrakulov.LanguageEnum;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.grishin.apartment.checker.telegram.KeyboardUtils.*;
import static java.util.stream.Collectors.toCollection;

@Slf4j
@Service
public class MainBotController {
    private final Map<Long, ConversationState> userStates = new HashMap<>();
    private final Map<Long, ApartmentFilter> userPreferences = new HashMap<>();
    private final Map<Long, String> selectedCommunities = new HashMap<>();
    private final Map<Long, Set<String>> userSelections = new ConcurrentHashMap<>();
    private final Map<Long, Integer> userPages = new ConcurrentHashMap<>();

    private final TelegramBotClient botClient;
    private final ApartmentsConfig apartmentsConfig;
    private final UserFilterService userFilterService;
    private final UnitAmenityRepository unitAmenityRepository;

    private final InlineCalendarBuilder inlineCalendarBuilder = new InlineCalendarBuilder(LanguageEnum.EN);
    public static final ZoneId BOT_TIME_ZONE = ZoneId.of("America/Los_Angeles");

    public MainBotController(
            @Value("${TELEGRAM_BOT_TOKEN}") String token,
            @Value("${telegram.bot.name}") String botName,
            ApartmentsConfig apartmentsConfig,
            UserFilterService userFilterService,
            UnitAmenityRepository unitAmenityRepository) {
        this.apartmentsConfig = apartmentsConfig;
        this.userFilterService = userFilterService;
        this.unitAmenityRepository = unitAmenityRepository;
        this.botClient = new TelegramBotClient(token, botName, this::onUpdateReceived);
    }

    @PostConstruct
    public void register() throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(botClient);
    }
    
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallbackQuery(update);
                return;
            }
            if (!update.hasMessage() || !update.getMessage().hasText())
                return;

            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            if (!userStates.containsKey(chatId))
                userStates.put(chatId, ConversationState.IDLE);

            if (messageText.equalsIgnoreCase("restart")) {
                log.info("Restart received. Restarting the session");
                newChat(chatId);
                return;
            }

            ConversationState currentState = userStates.get(chatId);
            log.info("Received message '{}' in state '{}'", messageText, currentState);
            switch (currentState) {
                case IDLE -> handleDefaultState(messageText, chatId);
                case WAITING_FOR_COMMUNITY -> {
                    processSelectedCommunity(chatId, messageText);
                    sendAmenitiesOptions(chatId);
                    userStates.put(chatId, ConversationState.WAITING_FOR_AMENITIES);
                }
                case SETTING_MIN_PRICE -> {
                    processMinPrice(chatId, messageText);
                    sendPriceRequest(chatId);
                }
                case SETTING_MAX_PRICE -> {
                    processMaxPrice(chatId, messageText);
                    sendDateRangeSlider(chatId, update);
                }
                case REVIEW_PREFERENCES -> {
                    if (messageText.equalsIgnoreCase("confirm")) {
                        saveUserPreferences(chatId);
                        sendFinalConfirmation(chatId);
                        userStates.put(chatId, ConversationState.IDLE);
                    } else if (messageText.equalsIgnoreCase("restart")) {
                        newChat(chatId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error during handling update", e);
        }
    }

    private void handleDefaultState(String messageText, long chatId) throws TelegramApiException {
        switch (messageText) {
            case "/start" -> newChat(chatId);
            case "/get_current_available" -> sendApartmentList(chatId);
            case "/unsubscribe" -> unsubscribeUser(chatId);
        }
    }

    private void unsubscribeUser(long chatId) {
        log.info("Unsubscribing user {}", chatId);
        userFilterService.clearUserFilters(chatId);
    }

    private void newChat(long chatId) throws TelegramApiException {
        sendCommunitySelection(chatId);
        userStates.put(chatId, ConversationState.WAITING_FOR_COMMUNITY);
        userPreferences.put(chatId, new ApartmentFilter());
    }

    private void handleCallbackQuery(Update update) {
        try {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();

            String[] parts = callbackData.split(":");
            switch (parts[0]) {
                case "select":
                    String option = callbackData.substring(7);
                    toggleSelection(chatId, option);
                    updateKeyboard(chatId, messageId);
                    break;
                case "selection.done":
                    List<String> selectedAmenities = userSelections.remove(chatId).stream().toList();
                    userPreferences.get(chatId).setAmenities(selectedAmenities);
                    userStates.put(chatId, ConversationState.SETTING_MIN_PRICE);
                    sendPriceRequest(chatId);
                    break;
                case "CAL_CM":
                    handleCalendarUpdate(update, parts, chatId, callbackData);
                    break;
                case "page":
                    int page = Integer.parseInt(callbackData.substring(5));
                    userPages.put(chatId, page);
                    updateApartmentList(chatId, messageId);
                    break;
            }

        } catch (NumberFormatException | TelegramApiException | ParseException e) {
            log.error("Error handling callback query", e);
            throw new RuntimeException(e);
        }
    }


    private void sendApartmentList(long chatId) throws TelegramApiException {
        int currentPage = userPages.getOrDefault(chatId, 0);
        ApartmentFilter filters = userFilterService.getUserFilters(chatId);
        if (filters == null) {
            sendMessage(chatId, "You don't have any preferences set. Please use /start to set them.");
            return;
        }
        List<Unit> allApartments = userFilterService.findApartmentsWithFilters(filters);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(generateApartmentListText(allApartments, currentPage));
        message.setReplyMarkup(generateApartmentListKeyboard(allApartments, currentPage));
        message.enableHtml(true);

        botClient.execute(message);
    }

    @Transactional
    private void updateApartmentList(long chatId, int messageId) throws TelegramApiException {
        int currentPage = userPages.getOrDefault(chatId, 0);
        ApartmentFilter filters = userFilterService.getUserFilters(chatId);
        if (filters == null) {
            sendMessage(chatId, "You don't have any preferences set. Please use /start to set them.");
            return;
        }
        List<Unit> apartmentsForUser = userFilterService.findApartmentsWithFilters(filters);

        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId(messageId);
        editMessage.setText(generateApartmentListText(apartmentsForUser, currentPage));
        editMessage.setReplyMarkup(generateApartmentListKeyboard(apartmentsForUser, currentPage));
        editMessage.enableHtml(true);

        botClient.execute(editMessage);
    }

    private String generateApartmentListText(List<Unit> apartments, int page) {

        int totalPages = (int) Math.ceil((double) apartments.size() / PAGE_SIZE);
        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, apartments.size());

        StringBuilder sb = new StringBuilder();
        if (startIndex >= apartments.size()) {
            sb.append("No relevant apartments found.");
            return sb.toString();
        }

        sb.append("<b>Available Apartments</b> (Page ").append(page + 1).append(" of ").append(totalPages).append(")\n\n");

        for (int i = startIndex; i < endIndex; i++) {
            Unit apt = apartments.get(i);
            sb.append(alertAvailableUnitMessage(UnitMessage.fromEntity(apt)));
        }

        return sb.toString();
    }

    private void toggleSelection(long chatId, String option) {
        Set<String> selections = userSelections.computeIfAbsent(chatId, k -> new HashSet<>());

        if (selections.contains(option)) {
            selections.remove(option);
        } else {
            selections.add(option);
        }
    }

    private void updateKeyboard(long chatId, int messageId) throws TelegramApiException {
        EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
        editMarkup.setChatId(String.valueOf(chatId));
        editMarkup.setMessageId(messageId);
        editMarkup.setReplyMarkup(generateSelectionKeyboard(chatId));

        botClient.execute(editMarkup);
    }

    private void handleCalendarUpdate(Update update, String[] parts, long chatId, String callbackData) throws TelegramApiException, ParseException {
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
                botClient.execute(sendMessage);
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
    }

    private void handleMaxDateCallback(Date dateValue, long chatId, Update update) throws TelegramApiException {
        ApartmentFilter preferences = userPreferences.get(chatId);
        LocalDate enteredDate = dateValue.toInstant()
                .atZone(BOT_TIME_ZONE)
                .toLocalDate();
        Date savedMinDate = preferences.getMinDate();
        LocalDate minDate = savedMinDate.toInstant()
                .atZone(BOT_TIME_ZONE)
                .toLocalDate();
        LocalDate maxPossibleDate = LocalDate.now(BOT_TIME_ZONE).plusDays(MAX_DATE_RANGE_DAYS);
        if (enteredDate.isAfter(minDate) && (enteredDate.isEqual(maxPossibleDate) || enteredDate.isBefore(maxPossibleDate)))  {
            log.info("Setting max date to {}", dateValue);
            preferences.setMaxDate(dateValue);
            userStates.put(chatId, ConversationState.REVIEW_PREFERENCES);
            showPreferencesSummary(chatId);
        } else {
            log.warn("Incorrect max date: {} is not between entered min date: {} and +3 months: {}, another try", dateValue, minDate, maxPossibleDate);
            sendMessage(chatId, "Entered date should be between minimum date and three months from today");
            sendDateRangeSlider(chatId, update);
        }
    }

    private void handleMinDateCallback(Date dateValue, long chatId, Update update) throws TelegramApiException {
        ApartmentFilter preferences = userPreferences.get(chatId);
        LocalDate enteredDate = dateValue.toInstant()
                .atZone(BOT_TIME_ZONE)
                .toLocalDate();
        LocalDate today = LocalDate.now(BOT_TIME_ZONE);
        LocalDate maxPossibleDate = today.plusDays(MAX_DATE_RANGE_DAYS);
        if ((enteredDate.isEqual(today) || enteredDate.isAfter(today)) && (enteredDate.isEqual(maxPossibleDate) || enteredDate.isBefore(maxPossibleDate))) {
            log.info("Setting min date to {}", dateValue);
            preferences.setMinDate(dateValue);
            userStates.put(chatId, ConversationState.SETTING_MAX_DATE);
            sendDateRangeSlider(chatId, update);
        } else {
            log.warn("Incorrect min date: {}, is not between today: {} and +3 months: {}, another try", dateValue, today, maxPossibleDate);
            sendMessage(chatId, "Entered date should be between today and three months from today");
            sendDateRangeSlider(chatId, update);
        }
    }

    private void sendPriceRequest(long chatId) throws TelegramApiException {
        ConversationState state = userStates.get(chatId);
        log.info("Sending price request to chatId: {} state : {}", chatId, state);
        String title;
        String buttonTest;
        if (state == ConversationState.SETTING_MIN_PRICE) {
            title = "Set minimum price:";
            buttonTest = "No Min Price";
        }
        else if (state == ConversationState.SETTING_MAX_PRICE) {
            title = "Set maximum price:";
            buttonTest = "No Max Price";
        }
        else throw new RuntimeException("Invalid state");

        SendMessage message = createMessageWithKeyboard(chatId, title, createKeyboardFromList(List.of(buttonTest)));
        botClient.execute(message);
    }

    private void processMinPrice(long chatId, String priceString) throws TelegramApiException {
        ConversationState state = userStates.get(chatId);
        log.info("Processing min price: {}, state: {}", priceString, state);

        if (state != ConversationState.SETTING_MIN_PRICE)
            throw new RuntimeException("Invalid state");

        int value = priceString.equals("No Min Price") ? MIN_PRICE : Integer.parseInt(priceString);

        ApartmentFilter preferences = userPreferences.get(chatId);
        if (value > MAX_PRICE || value < MIN_PRICE) {
            log.warn("Incorrect min price chosen: {}, another try", priceString);
            sendMessage(chatId, "Entered price should be between %d and %d".formatted(MIN_PRICE, MAX_PRICE));
            sendPriceRequest(chatId);
            return;
        }

        log.info("Setting min price to {}", value);
        preferences.setMinPrice(value);
        userStates.put(chatId, ConversationState.SETTING_MAX_PRICE);
    }

    private void processMaxPrice(long chatId, String priceString) throws TelegramApiException {
        ConversationState state = userStates.get(chatId);
        log.info("Processing max price: {}, state: {}", priceString, state);
        if (state != ConversationState.SETTING_MAX_PRICE)
            throw new RuntimeException("Invalid state");

        int enteredMaxPrice = priceString.equals("No Max Price") ? MAX_PRICE : Integer.parseInt(priceString);
        if (enteredMaxPrice > MAX_PRICE)
            enteredMaxPrice = MAX_PRICE;

        ApartmentFilter preferences = userPreferences.get(chatId);
        Integer chosenMinPrice = preferences.getMinPrice();
        if (enteredMaxPrice < chosenMinPrice) {
            log.warn("Incorrect max price chosen: {}, another try", priceString);
            sendMessage(chatId, "Entered price should be bigger than min price: %d".formatted(chosenMinPrice));
            sendPriceRequest(chatId);
            return;
        }

        log.info("Setting max price to {}", enteredMaxPrice);
        preferences.setMaxPrice(enteredMaxPrice);
        userStates.put(chatId, ConversationState.SETTING_MIN_DATE);
    }

    private void sendCommunitySelection(long chatId) throws TelegramApiException {
        List<String> communities = apartmentsConfig.getCommunities().stream().map(CommunityConfig::getName).toList();
        SendMessage message = createMessageWithKeyboard(
                chatId,
                "Please select your preferred community:",
                createKeyboardFromList(communities)
        );
        botClient.execute(message);
    }

    private void sendAmenitiesOptions(long chatId) throws TelegramApiException {
        userSelections.put(chatId, new HashSet<>());

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Please select one or more options:");
        message.setReplyMarkup(generateSelectionKeyboard(chatId));
        botClient.execute(message);
    }

    private InlineKeyboardMarkup generateSelectionKeyboard(long userId) {
        Set<String> selections = userSelections.getOrDefault(userId, new HashSet<>());
        List<String> amenities = unitAmenityRepository.findAll().stream()
                .map(UnitAmenity::getAmenityName).collect(toCollection(ArrayList::new));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        for (String option : amenities) {
            InlineKeyboardButton button = new InlineKeyboardButton();

            String text = selections.contains(option) ? "✅ " + option : "⬜️ " + option;
            button.setText(text);
            button.setCallbackData("select:" + option);

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            keyboard.add(row);
        }

        InlineKeyboardButton doneButton = new InlineKeyboardButton();
        doneButton.setText("Done ✓");
        doneButton.setCallbackData("selection.done");

        List<InlineKeyboardButton> doneRow = new ArrayList<>();
        doneRow.add(doneButton);
        keyboard.add(doneRow);

        markup.setKeyboard(keyboard);
        return markup;
    }

    private void processSelectedCommunity(long chatId, String selectedCommunity) {
        selectedCommunities.put(chatId, selectedCommunity);
    }

    public void sendDateRangeSlider(long chatId, Update update) throws TelegramApiException {
        ConversationState state = userStates.get(chatId);
        String title;
        if (state == ConversationState.SETTING_MIN_DATE)
            title = "Set minimum starting date:";
        else if (state == ConversationState.SETTING_MAX_DATE)
            title = "Set maximum starting date:";
        else throw new RuntimeException("Invalid state");

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(title);
        message.setReplyMarkup(inlineCalendarBuilder.build(update));

        botClient.execute(message);
    }

    private void showPreferencesSummary(long chatId) throws TelegramApiException {
        ApartmentFilter prefs = userPreferences.get(chatId);
        String selectedCommunity = selectedCommunities.get(chatId);

        StringBuilder summaryMessage = new StringBuilder();
        summaryMessage.append("⭐ Your Preferences Summary ⭐\n\n");
        summaryMessage.append("🏠 Community: ").append(selectedCommunity).append("\n\n");

        summaryMessage.append("🏷️ Filters: ").append(prefs.getAmenities()).append("\n\n");

        summaryMessage.append("💰 Price Range: ").append(prefs.getMinPrice()).append(" - ").append(prefs.getMaxPrice()).append(" $\n\n");

        Date minDate = prefs.getMinDate();
        Date maxDate = prefs.getMaxDate();
        summaryMessage.append("📅 Date Range: ").append(formatDate(minDate)).append(" - ").append(formatDate(maxDate)).append("\n\n");

        summaryMessage.append("Is this correct? Press 'confirm' to save or 'restart' to begin again.");
        SendMessage message = createMessageWithKeyboard(
                chatId,
                summaryMessage.toString(),
                createKeyboardFromList(List.of("Confirm", "Restart"))
        );
        botClient.execute(message);
    }

    private void sendFinalConfirmation(long chatId) throws TelegramApiException {
        String text = """
                ✅ Your preferences have been saved! You will receive updates based on your criteria.
                
                Type /start to set new preferences anytime or /get_current_available if you want to see current available apartments by your criteria.
                
                Type /unsubscribe to stop receiving updates.""";
        SendMessage message = createMessageWithKeyboard(chatId, text, clearKeyboard());
        botClient.execute(message);
    }

    private void saveUserPreferences(long chatId) {
        ApartmentFilter gatheredPreferences = userPreferences.remove(chatId);
        String selectedCommunityName = selectedCommunities.remove(chatId);
        CommunityConfig selectedCommunity = apartmentsConfig.getCommunities().stream()
                .filter(c -> c.getName().equals(selectedCommunityName))
                .findFirst().orElseThrow();
        userFilterService.saveUserFilters(chatId, selectedCommunity.getCommunityId(), gatheredPreferences);
    }

    public void sendMessage(long chatId, String text) throws TelegramApiException {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(text);
        botClient.execute(sendMessage);
    }
}
