package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
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
        return "garanttestdrivebot1bot"; // Замените на имя вашего бота
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

            boolean ordersFound = false;

            while (resultSet.next()) {
                ordersFound = true;
                int orderId = resultSet.getInt("order_id");
                String status = resultSet.getString("status");
                Timestamp createdAt = resultSet.getTimestamp("created_at");
                Timestamp deliveryDeadline = resultSet.getTimestamp("delivery_deadline");
                String mainStore = resultSet.getString("main_store");
                String customerAddress = resultSet.getString("customer_address");

                // Формируем текст для текущего заказа
                String orderText = String.format(
                        "📄 Номер заказа: %d\n" +
                                "📌 Статус: %s\n" +
                                "📅 Создан: %s\n" +
                                "⏰ Доставить до: %s\n" +
                                "🏬 Основной магазин: %s\n" +
                                "📍 Адрес покупателя: %s",
                        orderId,
                        status,
                        createdAt != null ? createdAt.toString() : "Нет данных",
                        deliveryDeadline != null ? deliveryDeadline.toString() : "Нет данных",
                        mainStore != null ? mainStore : "Нет данных",
                        customerAddress != null ? customerAddress : "Нет данных"
                );

                // Создаем кнопку для взятия заказа
                InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                List<InlineKeyboardButton> row = new ArrayList<>();

                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText("Взять в работу");
                button.setCallbackData("takeOrder_" + orderId);

                row.add(button);
                keyboard.add(row);
                keyboardMarkup.setKeyboard(keyboard);

                // Отправляем текст заказа с кнопкой
                SendMessage orderMessage = new SendMessage();
                orderMessage.setChatId(chatId);
                orderMessage.setText(orderText);
                orderMessage.setReplyMarkup(keyboardMarkup);

                execute(orderMessage);
            }

            if (!ordersFound) {
                // Если заказов нет, отправляем уведомление
                sendMessage(chatId, "❌ Доступных заказов нет.");
            }

        } catch (SQLException | TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка получения данных из базы: " + e.getMessage());
        }
    }


    private void sendMyOrders(long chatId) {
        String query = "SELECT * FROM orders WHERE courier_id = ?";
        StringBuilder response = new StringBuilder("📋 **Мои заказы:**\n");

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setLong(1, chatId); // Используем chatId как идентификатор курьера
            ResultSet resultSet = statement.executeQuery();

            boolean ordersFound = false;

            while (resultSet.next()) {
                ordersFound = true;
                int orderId = resultSet.getInt("order_id");
                String status = resultSet.getString("status");
                Timestamp deliveryDeadline = resultSet.getTimestamp("delivery_deadline");

                response.append(String.format(
                        "📄 Номер заказа: %d\n" +
                                "📌 Статус: %s\n" +
                                "⏰ Доставить до: %s\n\n",
                        orderId,
                        status,
                        deliveryDeadline != null ? deliveryDeadline.toString() : "Нет данных"
                ));
            }

            if (!ordersFound) {
                response.append("❌ У вас нет текущих заказов.");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            response.append("Ошибка получения данных из базы: ").append(e.getMessage());
        }

        sendMessage(chatId, response.toString());
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
