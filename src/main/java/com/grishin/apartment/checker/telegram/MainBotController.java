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
import java.time.ZoneOffset;
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
                    sendFilterMenu(chatId);
                }
                case SETTING_MIN_PRICE -> processMinPrice(chatId, messageText);
                case SETTING_MAX_PRICE -> processMaxPrice(chatId, messageText);
                case SETTING_MIN_FLOOR -> processMinFloor(chatId, messageText);
                case SETTING_MAX_FLOOR -> processMaxFloor(chatId, messageText);
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
                    sendFilterMenu(chatId);
                    break;
                case "filter":
                    handleFilterMenuCallback(parts[1], chatId, update);
                    break;
                case "unit_type":
                    handleUnitTypeCallback(parts[1], chatId);
                    break;
                case "skip_dates":
                    ApartmentFilter prefs = userPreferences.get(chatId);
                    prefs.setMinDate(null);
                    prefs.setMaxDate(null);
                    sendFilterMenu(chatId);
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

    private void handleFilterMenuCallback(String filterType, long chatId, Update update) throws TelegramApiException {
        switch (filterType) {
            case "amenities": {
                List<String> existing = userPreferences.get(chatId).getAmenities();
                userSelections.put(chatId, existing != null ? new HashSet<>(existing) : new HashSet<>());
                userStates.put(chatId, ConversationState.WAITING_FOR_AMENITIES);
                sendAmenitiesOptions(chatId);
                break;
            }
            case "price":
                userStates.put(chatId, ConversationState.SETTING_MIN_PRICE);
                sendPriceRequest(chatId);
                break;
            case "dates":
                userStates.put(chatId, ConversationState.SETTING_MIN_DATE);
                sendDateRangeSlider(chatId, update);
                break;
            case "unit_type":
                userStates.put(chatId, ConversationState.SETTING_UNIT_TYPE);
                sendUnitTypeOptions(chatId);
                break;
            case "floors":
                userStates.put(chatId, ConversationState.SETTING_MIN_FLOOR);
                sendFloorRequest(chatId);
                break;
            case "subscribe":
                saveUserPreferences(chatId);
                sendFinalConfirmation(chatId);
                userStates.put(chatId, ConversationState.IDLE);
                break;
        }
    }

    private void handleUnitTypeCallback(String unitType, long chatId) throws TelegramApiException {
        ApartmentFilter prefs = userPreferences.get(chatId);
        switch (unitType) {
            case "any":
                prefs.setIsStudio(null);
                prefs.setMinBedrooms(null);
                prefs.setMaxBedrooms(null);
                prefs.setMinBathrooms(null);
                prefs.setMaxBathrooms(null);
                prefs.setFloorPlanNameContains(null);
                break;
            case "studio":
                prefs.setIsStudio(true);
                prefs.setMinBedrooms(null);
                prefs.setMaxBedrooms(null);
                prefs.setMinBathrooms(null);
                prefs.setMaxBathrooms(null);
                prefs.setFloorPlanNameContains(null);
                break;
            case "1_1":
                prefs.setIsStudio(false);
                prefs.setMinBedrooms(1);
                prefs.setMaxBedrooms(1);
                prefs.setMinBathrooms(1);
                prefs.setMaxBathrooms(1);
                prefs.setFloorPlanNameContains(null);
                break;
            case "1_1_den":
                prefs.setIsStudio(false);
                prefs.setMinBedrooms(1);
                prefs.setMaxBedrooms(1);
                prefs.setMinBathrooms(1);
                prefs.setMaxBathrooms(1);
                prefs.setFloorPlanNameContains("den");
                break;
            case "2_2":
                prefs.setIsStudio(false);
                prefs.setMinBedrooms(2);
                prefs.setMaxBedrooms(2);
                prefs.setMinBathrooms(2);
                prefs.setMaxBathrooms(2);
                prefs.setFloorPlanNameContains(null);
                break;
            case "2_2_den":
                prefs.setIsStudio(false);
                prefs.setMinBedrooms(2);
                prefs.setMaxBedrooms(2);
                prefs.setMinBathrooms(2);
                prefs.setMaxBathrooms(2);
                prefs.setFloorPlanNameContains("den");
                break;
        }
        sendFilterMenu(chatId);
    }

    private void sendUnitTypeOptions(long chatId) throws TelegramApiException {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(makeRow(
                makeButton("Studio", "unit_type:studio"),
                makeButton("Any (skip)", "unit_type:any")
        ));
        keyboard.add(makeRow(
                makeButton("1bd / 1bath", "unit_type:1_1"),
                makeButton("2bd / 2bath", "unit_type:2_2")
        ));
        keyboard.add(makeRow(
                makeButton("1bd / 1bath + den", "unit_type:1_1_den"),
                makeButton("2bd / 2bath + den", "unit_type:2_2_den")
        ));

        markup.setKeyboard(keyboard);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Select unit type:");
        message.setReplyMarkup(markup);
        botClient.execute(message);
    }

    private void sendFilterMenu(long chatId) throws TelegramApiException {
        ApartmentFilter prefs = userPreferences.get(chatId);
        userStates.put(chatId, ConversationState.FILTER_MENU);

        // Clear any active reply keyboard with a summary message, then show inline filter menu
        SendMessage clearMsg = createMessageWithKeyboard(chatId, buildFilterSummaryText(prefs), clearKeyboard());
        botClient.execute(clearMsg);

        SendMessage menuMessage = new SendMessage();
        menuMessage.setChatId(String.valueOf(chatId));
        menuMessage.setText("Tap a filter to edit it:");
        menuMessage.setReplyMarkup(buildFilterMenuKeyboard(prefs));
        botClient.execute(menuMessage);
    }

    private String buildFilterSummaryText(ApartmentFilter prefs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Current filters:\n\n");

        List<String> amenities = prefs.getAmenities();
        if (amenities != null && !amenities.isEmpty())
            sb.append("Amenities: ").append(amenities.size()).append(" selected\n");
        else
            sb.append("Amenities: any\n");

        Integer minP = prefs.getMinPrice();
        Integer maxP = prefs.getMaxPrice();
        if (minP != null || maxP != null)
            sb.append("Price: $").append(minP != null ? minP : "any")
              .append(" - $").append(maxP != null ? maxP : "any").append("\n");
        else
            sb.append("Price: any\n");

        Date minD = prefs.getMinDate();
        Date maxD = prefs.getMaxDate();
        if (minD != null || maxD != null)
            sb.append("Dates: ")
              .append(minD != null ? formatDate(minD) : "any").append(" - ")
              .append(maxD != null ? formatDate(maxD) : "any").append("\n");
        else
            sb.append("Dates: any\n");

        sb.append("Unit type: ").append(getUnitTypeLabel(prefs)).append("\n");

        Integer minF = prefs.getMinFloor();
        Integer maxF = prefs.getMaxFloor();
        if (minF != null || maxF != null)
            sb.append("Floors: ")
              .append(minF != null ? minF : "any").append(" - ")
              .append(maxF != null ? maxF : "any").append("\n");
        else
            sb.append("Floors: any\n");

        return sb.toString();
    }

    private InlineKeyboardMarkup buildFilterMenuKeyboard(ApartmentFilter prefs) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(makeRow(
                makeButton(amenitiesLabel(prefs), "filter:amenities"),
                makeButton(priceLabel(prefs), "filter:price")
        ));
        keyboard.add(makeRow(
                makeButton(datesLabel(prefs), "filter:dates"),
                makeButton("Unit: " + getUnitTypeLabel(prefs), "filter:unit_type")
        ));
        keyboard.add(makeRow(
                makeButton(floorsLabel(prefs), "filter:floors")
        ));
        keyboard.add(makeRow(
                makeButton("Subscribe with current filters", "filter:subscribe")
        ));

        markup.setKeyboard(keyboard);
        return markup;
    }

    private String getUnitTypeLabel(ApartmentFilter prefs) {
        if (Boolean.TRUE.equals(prefs.getIsStudio())) return "Studio";
        if (prefs.getMinBedrooms() == null) return "any";
        String base = prefs.getMinBedrooms() + "bd/" + prefs.getMinBathrooms() + "bath";
        return prefs.getFloorPlanNameContains() != null ? base + "+den" : base;
    }

    private String amenitiesLabel(ApartmentFilter prefs) {
        List<String> a = prefs.getAmenities();
        return "Amenities" + (a != null && !a.isEmpty() ? ": " + a.size() + " sel." : " (any)");
    }

    private String priceLabel(ApartmentFilter prefs) {
        Integer min = prefs.getMinPrice();
        Integer max = prefs.getMaxPrice();
        if (min != null && max != null) return "Price: $" + min + "-$" + max;
        if (min != null) return "Price: $" + min + "+";
        if (max != null) return "Price: up to $" + max;
        return "Price (any)";
    }

    private String datesLabel(ApartmentFilter prefs) {
        Date minD = prefs.getMinDate();
        Date maxD = prefs.getMaxDate();
        if (minD != null || maxD != null) {
            String from = minD != null ? PRETTY_DATE_FORMAT.format(minD) : "any";
            String to = maxD != null ? PRETTY_DATE_FORMAT.format(maxD) : "any";
            return "Dates: " + from + "-" + to;
        }
        return "Dates (any)";
    }

    private String floorsLabel(ApartmentFilter prefs) {
        Integer min = prefs.getMinFloor();
        Integer max = prefs.getMaxFloor();
        if (min != null && max != null) return "Floors: " + min + "-" + max;
        if (min != null) return "Floors: " + min + "+";
        if (max != null) return "Floors: up to " + max;
        return "Floors (any)";
    }

    private InlineKeyboardButton makeButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private List<InlineKeyboardButton> makeRow(InlineKeyboardButton... buttons) {
        return new ArrayList<>(Arrays.asList(buttons));
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
        if (selections.contains(option))
            selections.remove(option);
        else
            selections.add(option);
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
                sendMessage.setReplyMarkup(buildCalendarMarkup(update));
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

    private InlineKeyboardMarkup buildCalendarMarkup(Update update) {
        InlineKeyboardMarkup calendar = inlineCalendarBuilder.build(update);
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>(calendar.getKeyboard());
        keyboard.add(makeRow(makeButton("Skip / Any date", "skip_dates")));
        calendar.setKeyboard(keyboard);
        return calendar;
    }

    private void handleMaxDateCallback(Date dateValue, long chatId, Update update) throws TelegramApiException {
        ApartmentFilter preferences = userPreferences.get(chatId);
        LocalDate enteredDate = dateValue.toInstant()
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        Date savedMinDate = preferences.getMinDate();
        LocalDate minDate = savedMinDate.toInstant()
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        LocalDate maxPossibleDate = LocalDate.now(BOT_TIME_ZONE).plusDays(MAX_DATE_RANGE_DAYS);
        if (enteredDate.isAfter(minDate) && (enteredDate.isEqual(maxPossibleDate) || enteredDate.isBefore(maxPossibleDate))) {
            log.info("Setting max date to {}", dateValue);
            preferences.setMaxDate(dateValue);
            sendFilterMenu(chatId);
        } else {
            log.warn("Incorrect max date: {} is not between entered min date: {} and +3 months: {}, another try", dateValue, minDate, maxPossibleDate);
            sendMessage(chatId, "Entered date should be between minimum date and three months from today");
            sendDateRangeSlider(chatId, update);
        }
    }

    private void handleMinDateCallback(Date dateValue, long chatId, Update update) throws TelegramApiException {
        ApartmentFilter preferences = userPreferences.get(chatId);
        LocalDate enteredDate = dateValue.toInstant()
                .atZone(ZoneOffset.UTC)
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
        String skipButton;
        if (state == ConversationState.SETTING_MIN_PRICE) {
            title = "Set minimum price:";
            skipButton = "No Min Price";
        } else if (state == ConversationState.SETTING_MAX_PRICE) {
            title = "Set maximum price:";
            skipButton = "No Max Price";
        } else throw new RuntimeException("Invalid state");

        SendMessage message = createMessageWithKeyboard(chatId, title, createKeyboardFromList(List.of(skipButton, "Cancel")));
        botClient.execute(message);
    }

    private void processMinPrice(long chatId, String priceString) throws TelegramApiException {
        if (priceString.equals("Cancel")) {
            sendFilterMenu(chatId);
            return;
        }

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
        sendPriceRequest(chatId);
    }

    private void processMaxPrice(long chatId, String priceString) throws TelegramApiException {
        if (priceString.equals("Cancel")) {
            sendFilterMenu(chatId);
            return;
        }

        int enteredMaxPrice = priceString.equals("No Max Price") ? MAX_PRICE : Integer.parseInt(priceString);
        if (enteredMaxPrice > MAX_PRICE)
            enteredMaxPrice = MAX_PRICE;

        ApartmentFilter preferences = userPreferences.get(chatId);
        Integer chosenMinPrice = preferences.getMinPrice();
        if (chosenMinPrice != null && enteredMaxPrice < chosenMinPrice) {
            log.warn("Incorrect max price chosen: {}, another try", priceString);
            sendMessage(chatId, "Entered price should be bigger than min price: %d".formatted(chosenMinPrice));
            sendPriceRequest(chatId);
            return;
        }

        log.info("Setting max price to {}", enteredMaxPrice);
        preferences.setMaxPrice(enteredMaxPrice);
        sendFilterMenu(chatId);
    }

    private void sendFloorRequest(long chatId) throws TelegramApiException {
        ConversationState state = userStates.get(chatId);
        String title;
        String skipButton;
        if (state == ConversationState.SETTING_MIN_FLOOR) {
            title = "Enter minimum floor:";
            skipButton = "No Min Floor";
        } else if (state == ConversationState.SETTING_MAX_FLOOR) {
            title = "Enter maximum floor:";
            skipButton = "No Max Floor";
        } else throw new RuntimeException("Invalid state");

        SendMessage message = createMessageWithKeyboard(chatId, title, createKeyboardFromList(List.of(skipButton, "Cancel")));
        botClient.execute(message);
    }

    private void processMinFloor(long chatId, String floorString) throws TelegramApiException {
        if (floorString.equals("Cancel")) {
            sendFilterMenu(chatId);
            return;
        }

        ApartmentFilter prefs = userPreferences.get(chatId);
        if (floorString.equals("No Min Floor")) {
            prefs.setMinFloor(null);
        } else {
            int floor = Integer.parseInt(floorString.trim());
            if (floor < 1) {
                sendMessage(chatId, "Floor must be at least 1");
                sendFloorRequest(chatId);
                return;
            }
            prefs.setMinFloor(floor);
        }
        userStates.put(chatId, ConversationState.SETTING_MAX_FLOOR);
        sendFloorRequest(chatId);
    }

    private void processMaxFloor(long chatId, String floorString) throws TelegramApiException {
        if (floorString.equals("Cancel")) {
            sendFilterMenu(chatId);
            return;
        }

        ApartmentFilter prefs = userPreferences.get(chatId);
        if (floorString.equals("No Max Floor")) {
            prefs.setMaxFloor(null);
        } else {
            int floor = Integer.parseInt(floorString.trim());
            Integer minFloor = prefs.getMinFloor();
            if (minFloor != null && floor < minFloor) {
                sendMessage(chatId, "Maximum floor must be greater than minimum floor: " + minFloor);
                sendFloorRequest(chatId);
                return;
            }
            prefs.setMaxFloor(floor);
        }
        sendFilterMenu(chatId);
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
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Select amenities, or tap Done to skip:");
        message.setReplyMarkup(generateSelectionKeyboard(chatId));
        botClient.execute(message);
    }

    private InlineKeyboardMarkup generateSelectionKeyboard(long userId) {
        Set<String> selections = userSelections.getOrDefault(userId, new HashSet<>());
        List<String> amenities = unitAmenityRepository.findAll().stream()
                .map(UnitAmenity::getAmenityName).collect(toCollection(ArrayList::new));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> currentRow = null;
        for (int i = 0; i < amenities.size(); i++) {
            String option = amenities.get(i);
            String text = selections.contains(option) ? "✅ " + option : "⬜️ " + option;

            if (i % 2 == 0) {
                currentRow = new ArrayList<>();
                keyboard.add(currentRow);
            }
            currentRow.add(makeButton(text, "select:" + option));
        }

        keyboard.add(makeRow(makeButton("Done ✓", "selection.done")));

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
        message.setReplyMarkup(buildCalendarMarkup(update));
        botClient.execute(message);
    }

    private void sendFinalConfirmation(long chatId) throws TelegramApiException {
        String text = """
                Your preferences have been saved! You will receive updates based on your criteria.

                Type /start to set new preferences anytime or /get_current_available to see current available apartments.

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
