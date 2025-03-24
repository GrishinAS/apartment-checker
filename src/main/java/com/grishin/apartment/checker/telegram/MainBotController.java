package com.grishin.apartment.checker.telegram;

import com.grishin.apartment.checker.config.ApartmentsConfig;
import com.grishin.apartment.checker.config.CommunityConfig;
import com.grishin.apartment.checker.dto.ApartmentFilter;
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
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
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
public class MainBotController extends TelegramLongPollingBot {
    private final Map<Long, ConversationState> userStates = new HashMap<>();
    private final Map<Long, ApartmentFilter> userPreferences = new HashMap<>();
    private final Map<Long, String> selectedCommunities = new HashMap<>();
    private final Map<Long, Set<String>> userSelections = new ConcurrentHashMap<>();
    private final Map<Long, Integer> userPages = new ConcurrentHashMap<>();

    private final ApartmentsConfig apartmentsConfig;
    private final UserFilterService userFilterService;
    private final UnitAmenityRepository unitAmenityRepository;

    private final InlineCalendarBuilder inlineCalendarBuilder = new InlineCalendarBuilder(LanguageEnum.EN);

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

    @PostConstruct
    public void register() throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(this);
    }

    @Override
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

            ConversationState currentState = userStates.get(chatId);
            log.info("Received message '{}' in state '{}'", messageText, currentState);
            switch (currentState) {
                case IDLE:
                    if (messageText.equals("/start")) {
                        sendCommunitySelection(chatId);
                        userStates.put(chatId, ConversationState.WAITING_FOR_COMMUNITY);
                        userPreferences.put(chatId, new ApartmentFilter());
                    } else if (messageText.equals("/getCurrentAvailable")) {
                        sendApartmentList(chatId);
                    }
                    break;

                case WAITING_FOR_COMMUNITY:
                    processSelectedCommunity(chatId, messageText);
                    sendAmenitiesOptions(chatId);
                    userStates.put(chatId, ConversationState.WAITING_FOR_AMENITIES);
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

    public void sendMessage(long chatId, String text) throws TelegramApiException {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(text);
        execute(sendMessage);
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
                case "page:":
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

        execute(message);
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

        execute(editMessage);
    }

    private String generateApartmentListText(List<Unit> apartments, int page) {
        int pageSize = 10;
        int totalPages = (int) Math.ceil((double) apartments.size() / pageSize);
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, apartments.size());

        StringBuilder sb = new StringBuilder();
        sb.append("<b>Available Apartments</b> (Page ").append(page + 1).append(" of ").append(totalPages).append(")\n\n");

        if (startIndex >= apartments.size()) {
            sb.append("No apartments to display on this page.");
            return sb.toString();
        }

        for (int i = startIndex; i < endIndex; i++) {
            Unit apt = apartments.get(i);
            sb.append(alertAvailableUnitMessage(apt));
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

        execute(editMarkup);
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
        LocalDate maxPossibleDate = LocalDate.now().plusDays(MAX_DATE_RANGE_DAYS);
        if (enteredDate.isAfter(minDate) && enteredDate.isBefore(maxPossibleDate)) {
            log.info("Setting max date to {}", dateValue);
            preferences.setMaxDate(dateValue);
            userStates.put(chatId, ConversationState.REVIEW_PREFERENCES);
            showPreferencesSummary(chatId);
        } else {
            log.warn("Incorrect max date chosen: {}, another try", dateValue);
            sendMessage(chatId, "Entered date should be between minimum date and three months from today");
            sendDateRangeSlider(chatId, update);
        }
    }

    private void handleMinDateCallback(Date dateValue, long chatId, Update update) throws TelegramApiException {
        ApartmentFilter preferences = userPreferences.get(chatId);
        LocalDate enteredDate = dateValue.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDate today = LocalDate.now();
        LocalDate maxPossibleDate = today.plusDays(MAX_DATE_RANGE_DAYS);
        if (enteredDate.isAfter(today) && enteredDate.isBefore(maxPossibleDate)) {
            log.info("Setting min date to {}", dateValue);
            preferences.setMinDate(dateValue);
            userStates.put(chatId, ConversationState.SETTING_MAX_DATE);
            sendDateRangeSlider(chatId, update);
        } else {
            log.warn("Incorrect min date chosen: {}, another try", dateValue);
            sendMessage(chatId, "Entered date should be between today and three months from today");
            sendDateRangeSlider(chatId, update);
        }
    }

    private void sendPriceRequest(long chatId) throws TelegramApiException {
        ConversationState state = userStates.get(chatId);
        log.info("Sending price request to chatId: {} state : {}", chatId, state);
        String title;
        if (state == ConversationState.SETTING_MIN_PRICE)
            title = "Set minimum price:";
        else if (state == ConversationState.SETTING_MAX_PRICE)
            title = "Set maximum price:";
        else throw new RuntimeException("Invalid state");
        sendMessage(chatId, title);
    }

    private void processPrice(long chatId, String priceString) throws TelegramApiException {
        int value = Integer.parseInt(priceString);
        ConversationState state = userStates.get(chatId);
        log.info("Processing price: {}, state: {}", value, state);
        ApartmentFilter preferences = userPreferences.get(chatId);
        if (value > MAX_PRICE || value < MIN_PRICE) {
            log.warn("Incorrect price chosen: {}, another try", priceString);
            sendMessage(chatId, "Entered price should be between %d and %d".formatted(MIN_PRICE, MAX_PRICE));
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
        List<String> communities = apartmentsConfig.getCommunities().stream().map(CommunityConfig::getName).toList();
        SendMessage message = createMessageWithKeyboard(
                chatId,
                "Please select your preferred community:",
                createKeyboardFromList(communities)
        );
        execute(message);
    }

    private void sendAmenitiesOptions(long chatId) throws TelegramApiException {
        userSelections.put(chatId, new HashSet<>());

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Please select one or more options:");
        message.setReplyMarkup(generateSelectionKeyboard(chatId));
        execute(message);
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

        Date minDate = prefs.getMinDate();
        Date maxDate = prefs.getMaxDate();
        summaryMessage.append("📅 Date Range: ").append(formatDate(minDate)).append(" - ").append(formatDate(maxDate)).append("\n\n");

        summaryMessage.append("Is this correct? Press 'confirm' to save or 'restart' to begin again.");
        SendMessage message = createMessageWithKeyboard(
                chatId,
                summaryMessage.toString(),
                createKeyboardFromList(List.of("Confirm", "Restart"))
        );
        execute(message);
    }

    private void sendFinalConfirmation(long chatId) throws TelegramApiException {
        String message = """
                ✅ Your preferences have been saved! You will receive updates based on your criteria.
                
                Type /start to set new preferences anytime or /getCurrentAvailable if you want to see current available apartments by your criteria.""";
        sendMessage(chatId, message);
    }

    private void saveUserPreferences(long chatId) {
        ApartmentFilter gatheredPreferences = userPreferences.remove(chatId);
        String selectedCommunity = selectedCommunities.remove(chatId);
        userFilterService.saveUserFilters(chatId, selectedCommunity, gatheredPreferences);
    }

    @Override
    public String getBotUsername() {
        return botName;
    }
}
