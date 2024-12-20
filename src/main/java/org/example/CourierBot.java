package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CourierBot extends TelegramLongPollingBot {
    private static final String DB_URL = "jdbc:sqlserver://localhost:1433;databaseName=SQLEXPRESS";
    private static final String DB_USER = "GARANT777\\Rodion";
    private static final String DB_PASSWORD = "170303";

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String chatId = update.getMessage().getChatId().toString();
            String messageText = update.getMessage().getText();

            switch (messageText.toLowerCase()) {
                case "/startshift":
                    startShift(chatId);
                    break;
                case "/endshift":
                    endShift(chatId);
                    break;
                case "/orders":
                    showAvailableOrders(chatId);
                    break;
                case "/takeorder":
                    sendMessage(chatId, "Введите номер заказа, который хотите взять:");
                    break;
                default:
                    if (messageText.startsWith("/take ")) {
                        takeOrder(chatId, messageText.replace("/take ", "").trim());
                    } else {
                        sendMessage(chatId, "Команда не распознана. Используйте: /startshift, /endshift, /orders, /takeorder.");
                    }
            }
        }
    }

    private void startShift(String chatId) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String query = "UPDATE Couriers SET IsOnShift = 1 WHERE ChatID = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, chatId);

            int rowsUpdated = preparedStatement.executeUpdate();
            if (rowsUpdated > 0) {
                sendMessage(chatId, "Вы начали смену!");
            } else {
                sendMessage(chatId, "Вы ещё не зарегистрированы. Обратитесь к администратору.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void endShift(String chatId) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String query = "UPDATE Couriers SET IsOnShift = 0 WHERE ChatID = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, chatId);

            preparedStatement.executeUpdate();
            sendMessage(chatId, "Вы завершили смену!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showAvailableOrders(String chatId) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String query = "SELECT ID, CustomerName, Address, OrderNumber, Status FROM Orders WHERE Status = 'Ожидает курьера'";
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);

            StringBuilder response = new StringBuilder("Доступные заказы:\n");
            while (resultSet.next()) {
                response.append("Номер: ").append(resultSet.getInt("ID")).append("\n")
                        .append("ФИО: ").append(resultSet.getString("CustomerName")).append("\n")
                        .append("Адрес: ").append(resultSet.getString("Address")).append("\n")
                        .append("Номер заказа: ").append(resultSet.getString("OrderNumber")).append("\n")
                        .append("Статус: ").append(resultSet.getString("Status")).append("\n\n");
            }

            sendMessage(chatId, response.length() > 0 ? response.toString() : "Нет доступных заказов.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void takeOrder(String chatId, String orderId) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String query = "UPDATE Orders SET Status = 'В работе', CourierID = (SELECT ID FROM Couriers WHERE ChatID = ?) WHERE ID = ? AND Status = 'Ожидает курьера'";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, chatId);
            preparedStatement.setString(2, orderId);

            int rowsUpdated = preparedStatement.executeUpdate();
            if (rowsUpdated > 0) {
                sendMessage(chatId, "Заказ взят в работу!");
            } else {
                sendMessage(chatId, "Не удалось взять заказ. Возможно, он уже занят.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(String chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return "garanttestdrivebot1bot";
    }

    @Override
    public String getBotToken() {
        return "7850699386:AAEc5eqsnUbEUm7tLp_rxU-k7wFmHUfELe8";
    }
}
