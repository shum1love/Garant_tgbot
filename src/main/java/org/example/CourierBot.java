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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CourierBot extends TelegramLongPollingBot {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/courier_service";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "1234";

    private final Map<Long, String> authorizedUsers = new HashMap<>();

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
        if (update.hasMessage()) {
            long chatId = update.getMessage().getChatId();

            if (!authorizedUsers.containsKey(chatId)) {
                if (update.getMessage().hasContact()) {
                    String phoneNumber = update.getMessage().getContact().getPhoneNumber();
                    authorizeUser(chatId, phoneNumber);
                } else {
                    requestPhoneNumber(chatId);
                }
                return;
            }

            if (update.getMessage().hasText()) {
                String messageText = update.getMessage().getText();

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

        if (update.hasCallbackQuery()) {
            String[] callbackData = update.getCallbackQuery().getData().split("_");
            if (callbackData[0].equals("takeOrder")) {
                long chatId = update.getCallbackQuery().getMessage().getChatId();
                int orderId = Integer.parseInt(callbackData[1]);
                takeOrder(chatId, orderId);
            }
        }
    }

    private void requestPhoneNumber(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("📱 Для авторизации отправьте ваш номер телефона.");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();
        KeyboardButton button = new KeyboardButton("Отправить номер телефона");
        button.setRequestContact(true);
        row.add(button);

        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void authorizeUser(long chatId, String phoneNumber) {
        phoneNumber = phoneNumber.replaceAll("\\s+", "").replaceFirst("^\\+7", "8");

        String query = "SELECT courier_id FROM couriers WHERE phone_number = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setString(1, phoneNumber);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                authorizedUsers.put(chatId, phoneNumber);
                sendMessage(chatId, "✅ Вы успешно авторизованы!");
                sendMainMenu(chatId);
            } else {
                sendMessage(chatId, "❌ Номер телефона не найден в системе. Обратитесь к администратору.");
                requestPhoneNumber(chatId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка базы данных: " + e.getMessage());
        }
    }

    private void sendMainMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Главное меню:");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Показать все доступные заказы"));
        row.add(new KeyboardButton("Мои заказы"));
        keyboardMarkup.setKeyboard(List.of(row));
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

                String orderText = String.format(
                        "📄 Номер заказа: %d\n" +
                                "📌 Статус: %s\n" +
                                "📅 Создан: %s",
                        orderId,
                        status,
                        createdAt != null ? createdAt.toString() : "Нет данных"
                );

                InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText("Взять в работу");
                button.setCallbackData("takeOrder_" + orderId);
                keyboard.add(List.of(button));
                keyboardMarkup.setKeyboard(keyboard);

                SendMessage orderMessage = new SendMessage();
                orderMessage.setChatId(chatId);
                orderMessage.setText(orderText);
                orderMessage.setReplyMarkup(keyboardMarkup);

                execute(orderMessage);
            }

            if (!ordersFound) {
                sendMessage(chatId, "❌ Доступных заказов нет.");
            }

        } catch (SQLException | TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка получения данных из базы: " + e.getMessage());
        }
    }

    private void takeOrder(long chatId, int orderId) {
        String phoneNumber = authorizedUsers.get(chatId);
        if (phoneNumber == null) {
            sendMessage(chatId, "Вы не авторизованы. Пожалуйста, отправьте номер телефона.");
            return;
        }

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE orders SET courier_id = (SELECT courier_id FROM couriers WHERE phone_number = ?), status = 'В работе' WHERE order_id = ?")) {

            statement.setString(1, phoneNumber);
            statement.setInt(2, orderId);

            int rowsUpdated = statement.executeUpdate();
            if (rowsUpdated > 0) {
                sendMessage(chatId, "✅ Заказ №" + orderId + " успешно взят в работу!");
            } else {
                sendMessage(chatId, "❌ Не удалось взять заказ в работу. Попробуйте позже.");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка базы данных: " + e.getMessage());
        }
    }

    private void sendMyOrders(long chatId) {
        String phoneNumber = authorizedUsers.get(chatId);
        if (phoneNumber == null) {
            sendMessage(chatId, "Вы не авторизованы.");
            return;
        }

        String query = "SELECT * FROM orders WHERE courier_id = (SELECT courier_id FROM couriers WHERE phone_number = ?)";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setString(1, phoneNumber);
            ResultSet resultSet = statement.executeQuery();

            StringBuilder response = new StringBuilder("📋 Мои заказы:\n");
            boolean ordersFound = false;

            while (resultSet.next()) {
                ordersFound = true;
                int orderId = resultSet.getInt("order_id");
                String status = resultSet.getString("status");
                response.append(String.format("📄 Номер заказа: %d\n📌 Статус: %s\n\n", orderId, status));
            }

            if (!ordersFound) {
                response.append("❌ У вас нет текущих заказов.");
            }

            sendMessage(chatId, response.toString());

        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка базы данных: " + e.getMessage());
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
