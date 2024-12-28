package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CourierBot extends TelegramLongPollingBot {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/courier_service";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "1234";

    @Override
    public String getBotUsername() {
        return "garanttestdrivebot1bot";
    }

    @Override
    public String getBotToken() {
        return "7850699386:AAEc5eqsnUbEUm7tLp_rxU-k7wFmHUfELe8";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            switch (messageText) {
                case "/start":
                    requestPhoneNumber(chatId);
                    break;
                case "Показать все доступные заказы":
                    sendAvailableOrders(chatId);
                    break;
                case "Мои заказы":
                    sendMyOrders(chatId);
                    break;
                default:
                    sendMessage(chatId, "Неизвестная команда. Используйте кнопки меню.");
            }
        } else if (update.hasMessage() && update.getMessage().hasContact()) {
            long chatId = update.getMessage().getChatId();
            String phoneNumber = update.getMessage().getContact().getPhoneNumber();

            if (saveCourierPhoneNumber(phoneNumber)) {
                sendMainMenu(chatId);
            } else {
                sendMessage(chatId, "❌ Ошибка сохранения номера телефона. Попробуйте снова.");
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.startsWith("takeOrder_")) {
                int orderId = Integer.parseInt(callbackData.split("_")[1]);
                takeOrder(chatId, orderId);
            } else if (callbackData.startsWith("cancelOrder_")) {
                int orderId = Integer.parseInt(callbackData.split("_")[1]);
                cancelOrder(chatId, orderId);
            }
        }
    }

    private void requestPhoneNumber(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Для продолжения работы, пожалуйста, предоставьте свой номер телефона:");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        KeyboardButton phoneButton = new KeyboardButton("Отправить номер телефона");
        phoneButton.setRequestContact(true);
        row.add(phoneButton);

        List<KeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private boolean saveCourierPhoneNumber(String phoneNumber) {
        String query = "INSERT INTO couriers (phone_number) VALUES (?) ON DUPLICATE KEY UPDATE phone_number = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setString(1, phoneNumber);
            statement.setString(2, phoneNumber);
            statement.executeUpdate();
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void sendMainMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Главное меню:");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Показать все доступные заказы"));
        row1.add(new KeyboardButton("Мои заказы"));

        keyboard.add(row1);

        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendAvailableOrders(long chatId) {
        String query = "SELECT * FROM orders WHERE status = 'Принят'";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {

            boolean hasOrders = false;
            while (resultSet.next()) {
                hasOrders = true;
                int orderId = resultSet.getInt("order_id");
                String orderInfo = String.format(
                        "📄 *Номер заказа:* %d\n" +
                                "📌 *Статус:* %s\n" +
                                "📅 *Создан:* %s\n" +
                                "📍 *Адрес покупателя:* %s\n" +
                                "📞 *Телефон покупателя:* %s\n",
                        orderId,
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at"),
                        resultSet.getString("customer_address"),
                        resultSet.getString("customer_phone")
                );

                InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(InlineKeyboardButton.builder()
                        .text("Взять в работу")
                        .callbackData("takeOrder_" + orderId)
                        .build());
                keyboard.add(row);
                keyboardMarkup.setKeyboard(keyboard);

                SendMessage message = SendMessage.builder()
                        .chatId(chatId)
                        .text(orderInfo)
                        .replyMarkup(keyboardMarkup)
                        .parseMode("Markdown")
                        .build();
                execute(message);
            }

            if (!hasOrders) {
                sendMessage(chatId, "Нет доступных заказов.");
            }

        } catch (SQLException | TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка получения данных из базы.");
        }
    }


    private void takeOrder(long chatId, int orderId) {
        // Здесь нужно получить номер телефона, переданный ранее.
        String phoneNumber = getPhoneNumberFromDatabase(chatId);
        if (phoneNumber == null) {
            sendMessage(chatId, "Ваш номер телефона не найден. Пожалуйста, предоставьте его снова.");
            return;
        }

        String query = "UPDATE orders SET status = 'В работе', courier_phone = ? WHERE order_id = ? AND status = 'Принят'";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setString(1, phoneNumber);
            statement.setInt(2, orderId);

            int updatedRows = statement.executeUpdate();
            if (updatedRows > 0) {
                sendMessage(chatId, "✅ Заказ №" + orderId + " успешно взят в работу.");
            } else {
                sendMessage(chatId, "⚠️ Не удалось взять заказ №" + orderId + ". Возможно, он уже взят другим курьером.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(chatId, "❌ Ошибка при взятии заказа №" + orderId + " в работу: " + e.getMessage());
        }
    }

    private void sendMyOrders(long chatId) {
        String query = "SELECT * FROM orders WHERE courier_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setLong(1, chatId);
            ResultSet resultSet = statement.executeQuery();

            boolean hasOrders = false;
            while (resultSet.next()) {
                hasOrders = true;
                int orderId = resultSet.getInt("order_id");
                String orderInfo = String.format(
                        "📄 Номер заказа: %d\n" +
                                "📌 Статус: %s\n" +
                                "📍 Адрес покупателя: %s\n" +
                                "📞 Телефон покупателя: %s\n\n",
                        orderId,
                        resultSet.getString("status"),
                        resultSet.getString("customer_address"),
                        resultSet.getString("customer_phone")
                );

                InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(InlineKeyboardButton.builder()
                        .text("Отказаться")
                        .callbackData("cancelOrder_" + orderId)
                        .build());
                keyboard.add(row);
                keyboardMarkup.setKeyboard(keyboard);

                SendMessage message = SendMessage.builder()
                        .chatId(chatId)
                        .text(orderInfo)
                        .replyMarkup(keyboardMarkup)
                        .build();
                execute(message);
            }

            if (!hasOrders) {
                sendMessage(chatId, "У вас пока нет активных заказов.");
            }

        } catch (SQLException | TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка получения данных из базы.");
        }
    }

    private void cancelOrder(long chatId, int orderId) {
        String query = "UPDATE orders SET status = 'Принят', courier_id = NULL WHERE order_id = ? AND courier_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setInt(1, orderId);
            statement.setLong(2, chatId);

            int updatedRows = statement.executeUpdate();
            if (updatedRows > 0) {
                sendMessage(chatId, "✅ Заказ №" + orderId + " успешно отменен.");
            } else {
                sendMessage(chatId, "⚠️ Не удалось отменить заказ №" + orderId + ". Возможно, он уже отменен или принадлежит другому курьеру.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(chatId, "❌ Ошибка при отмене заказа №" + orderId + ": " + e.getMessage());
        }
    }
    private String getPhoneNumberFromDatabase(long chatId) {
        String query = "SELECT phone_number FROM couriers WHERE chat_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setLong(1, chatId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("phone_number");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
