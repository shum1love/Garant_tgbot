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
        return "garanttestdrivebot1bot"; // Имя вашего бота
    }

    @Override
    public String getBotToken() {
        return "7850699386:AAEc5eqsnUbEUm7tLp_rxU-k7wFmHUfELe8"; // Токен вашего бота
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            switch (messageText) {
                case "/start":
                case "Старт":
                    sendMainMenu(chatId);
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
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.startsWith("takeOrder_")) {
                try {
                    int orderId = Integer.parseInt(callbackData.split("_")[1]);
                    takeOrder(chatId, orderId);
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "❌ Неверный формат ID заказа.");
                }
            }
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

                // Создаем клавиатуру с кнопкой "Взять в работу"
                InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(InlineKeyboardButton.builder()
                        .text("Взять в работу")
                        .callbackData("takeOrder_" + orderId)
                        .build());
                keyboard.add(row);
                keyboardMarkup.setKeyboard(keyboard);

                // Отправляем сообщение с текстом и кнопкой
                SendMessage message = SendMessage.builder()
                        .chatId(chatId)
                        .text(orderInfo)
                        .replyMarkup(keyboardMarkup)
                        .parseMode("Markdown") // Для форматирования текста
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
        String query = "UPDATE orders SET status = 'В работе', courier_id = ? WHERE order_id = ? AND status = 'Принят'";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setLong(1, chatId);
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

            StringBuilder response = new StringBuilder("📋 **Мои заказы:**\n");
            boolean hasOrders = false;

            while (resultSet.next()) {
                hasOrders = true;
                response.append(String.format(
                        "📄 Номер заказа: %d\n" +
                                "📌 Статус: %s\n" +
                                "📍 Адрес покупателя: %s\n" +
                                "📞 Телефон покупателя: %s\n\n",
                        resultSet.getInt("order_id"),
                        resultSet.getString("status"),
                        resultSet.getString("customer_address"),
                        resultSet.getString("customer_phone")
                ));
            }

            if (!hasOrders) {
                response.append("У вас пока нет активных заказов.");
            }

            sendMessage(chatId, response.toString());
        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка получения данных из базы.");
        }
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
