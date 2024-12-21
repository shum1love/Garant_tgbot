package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class CourierBot extends TelegramLongPollingBot {

    private static final String DB_URL = "jdbc:sqlite:garant.db";

    // Конструктор: создаём базу данных и заполняем тестовыми данными
    public CourierBot() {
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            String createTableQuery = "CREATE TABLE IF NOT EXISTS orders (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "order_number TEXT, " +
                    "status TEXT, " +
                    "address TEXT, " +
                    "recipient TEXT, " +
                    "payment_method TEXT, " +
                    "courier_id INTEGER DEFAULT NULL)";
            Statement statement = connection.createStatement();
            statement.execute(createTableQuery);

            /// Заполнение тестовыми данными
            String insertTestData = """
    INSERT INTO orders (order_number, status, address, recipient, payment_method)
    VALUES
    ('ORD001', 'Свободен', 'ул. Пушкина, д. 10', 'Иван Иванов', 'Карта'),
    ('ORD002', 'Свободен', 'ул. Лермонтова, д. 20', 'Мария Петрова', 'Наличные'),
    ('ORD003', 'Свободен', 'ул. Чехова, д. 5', 'Сергей Сергеев', 'Карта'),
    ('ORD004', 'Свободен', 'ул. Гоголя, д. 12', 'Анна Смирнова', 'Наличные'),
    ('ORD005', 'Свободен', 'ул. Толстого, д. 8', 'Петр Николаев', 'Карта'),
    ('ORD006', 'Свободен', 'ул. Тургенева, д. 15', 'Ольга Кузнецова', 'Наличные'),
    ('ORD007', 'Свободен', 'ул. Льва, д. 9', 'Дмитрий Алексеев', 'Карта'),
    ('ORD008', 'Свободен', 'ул. Некрасова, д. 22', 'Елена Морозова', 'Наличные');
""";
            statement.executeUpdate(insertTestData);
        } catch (Exception e) {
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

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String userMessage = update.getMessage().getText();
            long userId = update.getMessage().getFrom().getId();
            String chatId = update.getMessage().getChatId().toString();

            if ("/start".equals(userMessage)) {
                sendMessage(chatId, "Добро пожаловать! Используйте команды:\n" +
                        "/orders - Показать актуальные заказы\n" +
                        "/take_order <номер заказа> - Взять заказ");
            } else if ("/orders".equals(userMessage)) {
                String orders = getAvailableOrders();
                sendMessage(chatId, orders.isEmpty() ? "Нет доступных заказов." : orders);
            } else if (userMessage.startsWith("/take_order")) {
                String[] parts = userMessage.split(" ");
                if (parts.length == 2) {
                    String orderNumber = parts[1];
                    boolean result = takeOrder(orderNumber, userId);
                    sendMessage(chatId, result ? "Вы успешно взяли заказ " + orderNumber : "Не удалось взять заказ. Возможно, его уже взяли.");
                } else {
                    sendMessage(chatId, "Используйте формат: /take_order <номер заказа>");
                }
            } else {
                sendMessage(chatId, "Неизвестная команда. Используйте /start для справки.");
            }
        }
    }

    // Метод для отправки сообщения
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

    // Получение доступных заказов
    private String getAvailableOrders() {
        StringBuilder orders = new StringBuilder();
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            String selectQuery = "SELECT order_number, address, recipient, payment_method FROM orders WHERE status = 'Свободен'";
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(selectQuery);
            while (resultSet.next()) {
                orders.append("Номер заказа: ").append(resultSet.getString("order_number")).append("\n")
                        .append("Адрес: ").append(resultSet.getString("address")).append("\n")
                        .append("Получатель: ").append(resultSet.getString("recipient")).append("\n")
                        .append("Оплата: ").append(resultSet.getString("payment_method")).append("\n\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return orders.toString();
    }

    // Взять заказ
    private boolean takeOrder(String orderNumber, long courierId) {
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            String updateQuery = "UPDATE orders SET status = 'Взято', courier_id = ? WHERE order_number = ? AND status = 'Свободен'";
            PreparedStatement preparedStatement = connection.prepareStatement(updateQuery);
            preparedStatement.setLong(1, courierId);
            preparedStatement.setString(2, orderNumber);
            int rowsUpdated = preparedStatement.executeUpdate();
            return rowsUpdated > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
