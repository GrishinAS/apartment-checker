package com.grishin.apartment.checker.telegram;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class KeyboardUtils {
    public static final int MAX_DATE_RANGE_DAYS = 90;
    public static final int MIN_PRICE = 2000;
    public static final int MAX_PRICE = 5000;
    public static final int PRICE_STEP = 100;
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy");

    public static EditMessageText createPriceSliderMessage(long chatId, int messageId, int value, String type) {
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId(messageId);

        String title = type.equals("min") ? "Set minimum price:" : "Set maximum price:";
        editMessage.setText(title + " " + value + " ₽\n\n" + createPriceSliderView(value));

        return editMessage;
    }

    public static String createPriceSliderView(int currentValue) {
        StringBuilder slider = new StringBuilder();
        int position = (currentValue - MIN_PRICE) / PRICE_STEP;
        int totalPositions = (MAX_PRICE - MIN_PRICE) / PRICE_STEP;

        for (int i = 0; i <= totalPositions; i++) {
            if (i == position) {
                slider.append("🔘");
            } else {
                slider.append("⬜");
            }
        }

        return slider.toString();
    }

    public static InlineKeyboardMarkup createPriceSliderKeyboard(int currentValue, String callbackPrefix) {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();

        // Left decrement button (if not at min)
        if (currentValue > MIN_PRICE) {
            rowInline.add(createInlineButton("◀️", callbackPrefix + ":" + (currentValue - PRICE_STEP)));
        } else {
            rowInline.add(createInlineButton("⬛", "noop:0")); // Disabled button
        }

        // Current value button
        rowInline.add(createInlineButton(String.valueOf(currentValue), "noop:0"));

        // Right increment button (if not at max)
        if (currentValue < MAX_PRICE) {
            rowInline.add(createInlineButton("▶️", callbackPrefix + ":" + (currentValue + PRICE_STEP)));
        } else {
            rowInline.add(createInlineButton("⬛", "noop:0")); // Disabled button
        }

        rowsInline.add(rowInline);
        markupInline.setKeyboard(rowsInline);

        return markupInline;
    }



    public static EditMessageText createDateSliderMessage(long chatId, int messageId, Date date, String type) {
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId(messageId);
        String formattedDate = formatDate(date);

        String title = type.equals("min") ? "Set minimum date:" : "Set maximum date:";
        editMessage.setText(title + " " + formattedDate + "\n\n" + createDateSliderView(date));

        return editMessage;
    }

    public static String createDateSliderView(Date date) {
        Date today = new Date();
        Date maxPossibleDate = getDateWithOffset(90);
        long totalDays = (maxPossibleDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24);


        long daysFromToday = (date.getTime() - today.getTime()) / (1000 * 60 * 60 * 24);
        int position = (int) (daysFromToday * 9 / totalDays); // 9 positions (0-9)

        StringBuilder slider = new StringBuilder();
        for (int i = 0; i <= 9; i++) {
            if (i == position) {
                slider.append("🔘");
            } else {
                slider.append("⬜");
            }
        }

        return slider.toString();
    }

    public static InlineKeyboardMarkup createDateSliderKeyboard(Date currentDate, String callbackPrefix) {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();

        Date today = new Date();

        Date maxPossibleDate = getDateWithOffset(90);

        // Left button (move date back by 10 days if possible)
        if (currentDate.after(today)) {
            Date prevDate = getDateWithOffset(-10);
            if (prevDate.before(today)) prevDate = today;

            rowInline.add(createInlineButton("◀️", callbackPrefix + ":" + prevDate.getTime()));
        } else {
            rowInline.add(createInlineButton("⬛", "noop:0")); // Disabled button
        }

        // Current date button
        String formattedDate = formatDate(currentDate);
        rowInline.add(createInlineButton(formattedDate, "noop:0"));

        // Right button (move date forward by 10 days if possible)
        if (currentDate.before(maxPossibleDate)) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(currentDate);
            cal.add(Calendar.DAY_OF_MONTH, 10);
            Date nextDate = cal.getTime();
            if (nextDate.after(maxPossibleDate)) nextDate = maxPossibleDate;

            rowInline.add(createInlineButton("▶️", callbackPrefix + ":" + nextDate.getTime()));
        } else {
            rowInline.add(createInlineButton("⬛", "noop:0")); // Disabled button
        }

        rowsInline.add(rowInline);
        markupInline.setKeyboard(rowsInline);

        return markupInline;
    }

    public static Date getDateWithOffset(int dayOffset) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, dayOffset);
        return calendar.getTime();
    }

    public static String formatDate(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy");
        return formatter.format(date);
    }

    public static InlineKeyboardButton createInlineButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    public static ReplyKeyboardMarkup createKeyboardFromList(List<String> options) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        // Create rows with 2 buttons per row
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

    public static SendMessage createMessageWithKeyboard(long chatId, String text, ReplyKeyboardMarkup keyboardMarkup) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setReplyMarkup(keyboardMarkup);
        return message;
    }
}
