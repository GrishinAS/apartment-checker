package com.grishin.apartment.checker.telegram;

import com.grishin.apartment.checker.dto.UnitMessage;
import com.grishin.apartment.checker.storage.entity.Unit;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class KeyboardUtils {
    public static final int MAX_DATE_RANGE_DAYS = 90;
    public static final int MIN_PRICE = 1000;
    public static final int MAX_PRICE = 5000;
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy");
    public static final SimpleDateFormat PRETTY_DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");
    public static final int PAGE_SIZE = 5;

    public static InlineKeyboardMarkup generateApartmentListKeyboard(List<Unit> apartments, int page) {
        int totalPages = (int) Math.ceil((double) apartments.size() / PAGE_SIZE);
        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, apartments.size());

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();


        List<InlineKeyboardButton> navigationRow = new ArrayList<>();

        if (page > 0) {
            InlineKeyboardButton prevButton = new InlineKeyboardButton();
            prevButton.setText("◀️ Previous");
            prevButton.setCallbackData("page:" + (page - 1));
            navigationRow.add(prevButton);
        }

        InlineKeyboardButton pageButton = new InlineKeyboardButton();
        pageButton.setText((page + 1) + "/" + totalPages);
        pageButton.setCallbackData("noop");
        navigationRow.add(pageButton);

        if (page < totalPages - 1) {
            InlineKeyboardButton nextButton = new InlineKeyboardButton();
            nextButton.setText("Next ▶️");
            nextButton.setCallbackData("page:" + (page + 1));
            navigationRow.add(nextButton);
        }

        keyboard.add(navigationRow);
        markup.setKeyboard(keyboard);

        return markup;
    }

    public static String alertAvailableUnitMessage(UnitMessage unit) {
        StringBuilder message = new StringBuilder();
        message.append("\n");
        message.append("<b>Apartment ").append(unit.getBuildingNumber()).append(" ").append(unit.getUnitMarketingName()).append("</b>\n");
        if (unit.isStudio()) {
            message.append("Studio");
        } else {
            message.append("Bedrooms: ").append(unit.getBedrooms()).append("\n");
            message.append("Bathrooms: ").append(unit.getBathrooms()).append("\n");
        }
        message.append("Floor: ").append(unit.getFloor()).append("\n");
        message.append("Price: $").append(unit.getPrice()).append("\n");
        message.append("Floorplan: ").append(unit.getFloorPlanName()).append("\n");
        message.append("Amenities:\n");
        unit.getAmenityNames().forEach(amenity -> message.append("   ").append(amenity).append("\n"));
        Date availableDate = unit.getAvailableFrom();
        message.append("Available From: ").append(PRETTY_DATE_FORMAT.format(availableDate));
        message.append("\n");
        return message.toString();
    }

    public static String formatDate(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy");
        return formatter.format(date);
    }

    public static ReplyKeyboardMarkup createKeyboardFromList(List<String> options) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        for (int i = 0; i < options.size(); i += 2) {
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton(options.get(i)));

            if (i + 1 < options.size()) {
                row.add(new KeyboardButton(options.get(i + 1)));
            }

            keyboard.add(row);
        }

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    public static SendMessage createMessageWithKeyboard(long chatId, String text, ReplyKeyboard keyboardMarkup) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setReplyMarkup(keyboardMarkup);
        return message;
    }

    public static ReplyKeyboardRemove clearKeyboard() {
        ReplyKeyboardRemove keyboard = new ReplyKeyboardRemove();
        keyboard.setRemoveKeyboard(true);

        return keyboard;
    }
}
