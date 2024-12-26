package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CourierBot extends TelegramLongPollingBot {

    // Конфигурация подключения к базе данных
    private static final String DB_URL = "jdbc:mysql://localhost:3306/courier_service";
    private static final String DB_USER = "tgbot"; // Укажите вашего пользователя
    private static final String DB_PASSWORD = "1234"; // Укажите ваш пароль

    @Override
    public String getBotUsername() {
        return "garanttestdrivebot1bot"; // Имя бота
    }

    @Override
    public String getBotToken() {
        return "7850699386:AAEc5eqsnUbEUm7tLp_rxU-k7wFmHUfELe8"; // Замените на токен вашего бота
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.equals("/start")) {
                sendStartMessage(chatId);
            } else if (messageText.startsWith("/status")) {
                String[] parts = messageText.split(" ");
                if (parts.length == 2) {
                    int orderId;
                    try {
                        orderId = Integer.parseInt(parts[1]);
                        String orderInfo = getOrderInfo(orderId);
                        sendMessage(chatId, orderInfo);
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Пожалуйста, введите корректный номер заказа.");
                    }
                } else {
                    sendMessage(chatId, "Используйте формат: /status [номер заказа]");
                }
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.equals("SHOW_ORDERS")) {
                String orders = getAvailableOrders();
                sendMessage(chatId, orders);
            }
        }
    }

    private void sendStartMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Добро пожаловать! Нажмите кнопку ниже, чтобы увидеть заказы, которые можно взять в работу.");

        // Добавление кнопки "Начать"
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton startButton = new InlineKeyboardButton("Показать доступные заказы");
        startButton.setCallbackData("SHOW_ORDERS");
        row.add(startButton);
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private String getAvailableOrders() {
        StringBuilder result = new StringBuilder("📋 **Доступные заказы:**\n");
        String query = "SELECT * FROM orders WHERE status = 'Принят'";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                result.append(String.format(
                        "📄 Номер заказа: %d\n" +
                                "📌 Статус: %s\n" +
                                "📅 Создан: %s\n" +
                                "⏰ Доставить до: %s\n" +
                                "🏬 Основной магазин: %s\n" +
                                "📦 Магазин дотарки: %s\n" +
                                "📍 Адрес покупателя: %s\n" +
                                "📞 Телефон покупателя: %s\n",
                        resultSet.getInt("order_id"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at"),
                        resultSet.getTimestamp("delivery_deadline"),
                        resultSet.getString("main_store"),
                        resultSet.getString("secondary_store") != null ? resultSet.getString("secondary_store") : "Нет",
                        resultSet.getString("customer_address"),
                        resultSet.getString("customer_phone")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Ошибка получения данных из базы.";
        }

        if (result.toString().equals("📋 **Доступные заказы:**\n")) {
            return "На данный момент доступных заказов нет.";
        }

        return result.toString();
    }

    private String getOrderInfo(int orderId) {
        String query = "SELECT * FROM orders WHERE order_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setInt(1, orderId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                return String.format(
                        "🛒 **Информация о заказе:**\n" +
                                "📄 Номер заказа: %d\n" +
                                "📌 Статус: %s\n" +
                                "📅 Создан: %s\n" +
                                "⏰ Доставить до: %s\n" +
                                "🏬 Основной магазин: %s\n" +
                                "📦 Магазин дотарки: %s\n" +
                                "📍 Адрес покупателя: %s\n" +
                                "📞 Телефон покупателя: %s\n",
                        resultSet.getInt("order_id"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at"),
                        resultSet.getTimestamp("delivery_deadline"),
                        resultSet.getString("main_store"),
                        resultSet.getString("secondary_store") != null ? resultSet.getString("secondary_store") : "Нет",
                        resultSet.getString("customer_address"),
                        resultSet.getString("customer_phone")
                );
            } else {
                return "Заказ не найден.";
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Ошибка подключения к базе данных.";
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
