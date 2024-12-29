package org.example;

// Импорты необходимых библиотек и классов для работы с Telegram Bot API, базой данных и коллекциями.
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// Основной класс, наследующий TelegramLongPollingBot, что позволяет обрабатывать входящие сообщения от пользователей.
public class CourierBot extends TelegramLongPollingBot {

    public CourierBot() {
        super(new DefaultBotOptions(), "7850699386:AAEc5eqsnUbEUm7tLp_rxU-k7wFmHUfELe8");
    }

    // Константы для подключения к базе данных.
    private static final String DB_URL = "jdbc:mysql://localhost:3306/courier_service"; // URL базы данных.
    private static final String DB_USER = "root"; // Имя пользователя базы данных.
    private static final String DB_PASSWORD = "1234"; // Пароль пользователя базы данных.

    // Метод, возвращающий имя бота.
    @Override
    public String getBotUsername() {
        return "garanttestdrivebot1bot"; // Имя бота в Telegram.
    }

    // Метод, возвращающий токен бота для авторизации в Telegram API.
    @Override
    public String getBotToken() {
        return "7850699386:AAEc5eqsnUbEUm7tLp_rxU-k7wFmHUfELe8"; // Токен, выданный BotFather.
    }

    // Метод, который вызывается при получении нового сообщения или события от пользователя.
    @Override
    public void onUpdateReceived(Update update) {
        try {
            // Обработка команды /start
            if (update.hasMessage()) {
                long chatId = update.getMessage().getChatId();
                
                if (update.getMessage().hasText()) {
                    String messageText = update.getMessage().getText();
                    
                    switch (messageText) {
                        case "/start":
                            requestPhoneNumber(chatId);
                            return;
                        case "Показать все доступные заказы":
                            sendAvailableOrders(chatId);
                            return;
                        case "Мои заказы":
                            sendMyOrders(chatId);
                            return;
                    }
                }
                
                // Обработка получения контакта
                if (update.getMessage().hasContact()) {
                    String phoneNumber = update.getMessage().getContact().getPhoneNumber();
                    if (saveCourierPhoneNumber(phoneNumber)) {
                        sendMainMenu(chatId);
                    } else {
                        sendMessage(chatId, "❌ Ошибка сохранения номера телефона. Попробуйте снова.");
                    }
                    return;
                }
            }

            if (update.hasCallbackQuery()) {
                String callbackData = update.getCallbackQuery().getData();
                long chatId = update.getCallbackQuery().getMessage().getChatId();

                AnswerCallbackQuery answer = new AnswerCallbackQuery();
                answer.setCallbackQueryId(update.getCallbackQuery().getId());
                execute(answer);

                if (callbackData.startsWith("takeOrder_")) {
                    int orderId = Integer.parseInt(callbackData.split("_")[1]);
                    takeOrder(chatId, orderId);
                } else if (callbackData.startsWith("completeOrder_")) {
                    int orderId = Integer.parseInt(callbackData.split("_")[1]);
                    completeOrder(chatId, orderId);
                } else if (callbackData.startsWith("cancelOrder_")) {
                    int orderId = Integer.parseInt(callbackData.split("_")[1]);
                    cancelOrder(chatId, orderId);
                } else if (callbackData.equals("SHOW_ORDERS")) {
                    sendAvailableOrders(chatId);
                } else if (callbackData.equals("MY_ORDERS")) {
                    sendMyOrders(chatId);
                }
            }
            // ... остальной код ...
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Метод для запроса номера телефона у пользователя.
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

    // Метод для сохранения номера телефона в базу данных.
    private boolean saveCourierPhoneNumber(String phoneNumber) {
        // Очищаем номер телефона от лишних символов
        phoneNumber = phoneNumber.replaceAll("[^0-9]", "");
        
        // Если номер начинается с 8, заменяем на 7
        if (phoneNumber.startsWith("8")) {
            phoneNumber = "7" + phoneNumber.substring(1);
        }
        
        // Если номер не начинается с 7, добавляем его
        if (!phoneNumber.startsWith("7")) {
            phoneNumber = "7" + phoneNumber;
        }
        
        String checkQuery = "SELECT phone_number FROM couriers WHERE phone_number = ?";
        String insertQuery = "INSERT INTO couriers (phone_number) VALUES (?)";
        
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Проверяем существование номера
            try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
                checkStmt.setString(1, phoneNumber);
                ResultSet rs = checkStmt.executeQuery();
                
                if (rs.next()) {
                    return true;
                }
                
                // Если номера нет, добавляем его
                try (PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
                    insertStmt.setString(1, phoneNumber);
                    insertStmt.executeUpdate();
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Метод для отображения главного меню
    private void sendMainMenu(long chatId) {
        // Создаем сообщение для отображения главного меню
        SendMessage message = new SendMessage();
        message.setChatId(chatId); // Устанавливаем ID чата
        message.setText("Главное меню:"); // Текст сообщения

        // Создаем клавиатуру
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>(); // Список строк клавиатуры

        // Создаем первую строку с кнопками
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Показать все доступные заказы")); // Кнопка для отображения доступных заказов
        row1.add(new KeyboardButton("Мои заказы")); // Кнопка для отображения заказов пользователя

        keyboard.add(row1); // Добавляем строку в клавиатуру

        // Устанавливаем клавиатуру для сообщения
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true); // Делаем клавиатуру компактной
        message.setReplyMarkup(keyboardMarkup);

        // Пытаемся отправить сообщение
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Метод для отображения доступных заказов
    private void sendAvailableOrders(long chatId) {
        String query = "SELECT * FROM orders WHERE status = 'Принят' AND courier_phone IS NULL";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            ResultSet resultSet = statement.executeQuery();
            boolean hasOrders = false;
            
            while (resultSet.next()) {
                hasOrders = true;
                int orderId = resultSet.getInt("order_id");
                String orderInfo = String.format(
                    "📄 *Заказ №%d*\n" +
                    "📌 Статус: %s\n" +
                    "📅 Создан: %s\n" +
                    "⏰ Доставить до: %s\n" +
                    "🏬 Магазин: %s\n" +
                    "%s" + // Дополнительный магазин (если есть)
                    "📍 Адрес: %s\n" +
                    "📞 Телефон: %s",
                    orderId,
                    resultSet.getString("status"),
                    resultSet.getTimestamp("created_at"),
                    resultSet.getTimestamp("delivery_deadline"),
                    resultSet.getString("main_store"),
                    resultSet.getString("secondary_store") != null ? 
                        "🏬 Доп. магазин: " + resultSet.getString("secondary_store") + "\n" : "",
                    resultSet.getString("customer_address"),
                    resultSet.getString("customer_phone")
                );

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(InlineKeyboardButton.builder()
                    .text("Взять заказ")
                    .callbackData("takeOrder_" + orderId)
                    .build());
                keyboard.add(row);
                markup.setKeyboard(keyboard);

                SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text(orderInfo)
                    .replyMarkup(markup)
                    .parseMode("Markdown")
                    .build();
                execute(message);
            }

            if (!hasOrders) {
                sendMessage(chatId, "Нет доступных заказов");
            }
        } catch (SQLException | TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка при получении списка заказов");
        }
    }

    // Вспомогательный метод для отправки сообщений
    private void sendMessage(long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Метод для отображения заказов пользователя
    private void sendMyOrders(long chatId) {
        // Получаем номер телефона курьера напрямую из таблицы courier_orders
        String query = 
            "SELECT DISTINCT o.* FROM orders o " +
            "INNER JOIN courier_orders co ON o.order_id = co.order_id " +
            "WHERE co.order_status = 'В работе' " +
            "AND co.courier_phone = (SELECT phone_number FROM couriers LIMIT 1)";
            
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            try (ResultSet resultSet = statement.executeQuery()) {
                boolean hasOrders = false;
                
                while (resultSet.next()) {
                    hasOrders = true;
                    String orderInfo = String.format(
                        "📄 *Заказ №%d*\n" +
                        "📌 Статус: %s\n" +
                        "📅 Создан: %s\n" +
                        "⏰ Доставить до: %s\n" +
                        "🏬 Основной магазин: %s\n" +
                        "📦 Магазин дотарки: %s\n" +
                        "📍 Адрес: %s\n" +
                        "📞 Телефон клиента: %s",
                        resultSet.getInt("order_id"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at"),
                        resultSet.getTimestamp("delivery_deadline"),
                        resultSet.getString("main_store"),
                        resultSet.getString("secondary_store") != null ? 
                            resultSet.getString("secondary_store") : "Нет",
                        resultSet.getString("customer_address"),
                        resultSet.getString("customer_phone")
                    );
                    
                    // Создаем кнопки для заказа
                    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                    
                    // Первый ряд кнопок
                    List<InlineKeyboardButton> row1 = new ArrayList<>();
                    InlineKeyboardButton cancelButton = new InlineKeyboardButton();
                    cancelButton.setText("❌ Отменить заказ");
                    cancelButton.setCallbackData("cancelOrder_" + resultSet.getInt("order_id"));
                    row1.add(cancelButton);
                    
                    // Второй ряд кнопок
                    List<InlineKeyboardButton> row2 = new ArrayList<>();
                    InlineKeyboardButton completeButton = new InlineKeyboardButton();
                    completeButton.setText("✅ Завершить заказ");
                    completeButton.setCallbackData("completeOrder_" + resultSet.getInt("order_id"));
                    row2.add(completeButton);
                    
                    keyboard.add(row1);
                    keyboard.add(row2);
                    markup.setKeyboard(keyboard);
                    
                    // Отправляем сообщение с информацией о заказе и кнопками
                    SendMessage message = new SendMessage();
                    message.setChatId(String.valueOf(chatId));
                    message.setText(orderInfo);
                    message.setParseMode("Markdown");
                    message.setReplyMarkup(markup);
                    
                    execute(message);
                }
                
                if (!hasOrders) {
                    sendMessage(chatId, "У вас пока нет активных заказов. " +
                        "Чтобы взять заказ, перейдите в раздел \"Доступные заказы\".");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка при получении списка заказов: " + e.getMessage());
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка при отправке сообщения: " + e.getMessage());
        }
    }

    // Метод для обработки взятия заказа курьером
    private void takeOrder(long chatId, int orderId) {
        String query = "SELECT phone_number FROM couriers WHERE phone_number = (SELECT courier_phone FROM orders WHERE order_id = ?)";
        
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Проверяем, не взят ли уже заказ
            try (PreparedStatement checkStatement = connection.prepareStatement(query)) {
                checkStatement.setInt(1, orderId);
                ResultSet rs = checkStatement.executeQuery();
                
                if (rs.next()) {
                    sendMessage(chatId, "❌ Этот заказ уже взят другим курьером.");
                    return;
                }
            }
            
            // Получаем номер телефона курьера из базы
            String courierPhone = null;
            String phoneQuery = "SELECT phone_number FROM couriers";
            try (PreparedStatement phoneStatement = connection.prepareStatement(phoneQuery)) {
                ResultSet rs = phoneStatement.executeQuery();
                if (rs.next()) {
                    courierPhone = rs.getString("phone_number");
                }
            }
            
            if (courierPhone == null) {
                sendMessage(chatId, "❌ Пожалуйста, сначала зарегистрируйтесь, отправив свой номер телефона.");
                return;
            }

            // Добавляем запись в courier_orders
            String insertQuery = 
                "INSERT INTO courier_orders (courier_phone, order_id, order_status) VALUES (?, ?, 'В работе')";
            
            try (PreparedStatement insertStatement = connection.prepareStatement(insertQuery)) {
                insertStatement.setString(1, courierPhone);
                insertStatement.setInt(2, orderId);
                insertStatement.executeUpdate();
            }

            // Обновляем статус и номер курьера в таблице orders
            String updateQuery = 
                "UPDATE orders SET status = 'В работе', courier_phone = ? WHERE order_id = ?";
            
            try (PreparedStatement updateStatement = connection.prepareStatement(updateQuery)) {
                updateStatement.setString(1, courierPhone);
                updateStatement.setInt(2, orderId);
                int updatedRows = updateStatement.executeUpdate();
                
                if (updatedRows > 0) {
                    // Отправляем сообщение об успешном взятии заказа
                    String successMessage = String.format(
                        "✅ Заказ №%d успешно взят в работу!\n" +
                        "Теперь вы можете увидеть его в разделе \"Мои заказы\"", 
                        orderId
                    );
                    sendMessage(chatId, successMessage);
                } else {
                    sendMessage(chatId, "❌ Не удалось взять заказ. Возможно, он уже занят или был отменён.");
                }
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            String errorMessage = "Произошла ошибка при взятии заказа: " + e.getMessage();
            System.err.println(errorMessage);
            sendMessage(chatId, "❌ Не удалось взять заказ. Пожалуйста, попробуйте позже.");
        }
    }

    // Метод для обработки отмены заказа курьером
    private void cancelOrder(long chatId, int orderId) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            connection.setAutoCommit(false);
            try {
                // Получаем номер телефона курьера
                String phoneQuery = "SELECT phone_number FROM couriers LIMIT 1";
                String courierPhone = null;
                
                try (PreparedStatement phoneStmt = connection.prepareStatement(phoneQuery)) {
                    ResultSet rs = phoneStmt.executeQuery();
                    if (rs.next()) {
                        courierPhone = rs.getString("phone_number");
                    }
                }
                
                if (courierPhone != null) {
                    // Обновляем статус в таблице orders
                    String updateOrderQuery = "UPDATE orders SET status = 'Принят', courier_phone = NULL " +
                        "WHERE order_id = ? AND courier_phone = ?";
                    
                    // Обновляем статус в таблице courier_orders
                    String updateCourierOrderQuery = "UPDATE courier_orders SET order_status = 'Отменен' " +
                        "WHERE order_id = ? AND courier_phone = ?";
                    
                    try (PreparedStatement orderStmt = connection.prepareStatement(updateOrderQuery);
                         PreparedStatement courierOrderStmt = connection.prepareStatement(updateCourierOrderQuery)) {
                        
                        orderStmt.setInt(1, orderId);
                        orderStmt.setString(2, courierPhone);
                        courierOrderStmt.setInt(1, orderId);
                        courierOrderStmt.setString(2, courierPhone);
                        
                        int updatedOrders = orderStmt.executeUpdate();
                        int updatedCourierOrders = courierOrderStmt.executeUpdate();
                        
                        if (updatedOrders > 0 && updatedCourierOrders > 0) {
                            connection.commit();
                            sendMessage(chatId, "❌ Заказ №" + orderId + " отменен.");
                        } else {
                            connection.rollback();
                            sendMessage(chatId, "Не удалось отменить заказ. Возможно, он вам не принадлежит.");
                        }
                    }
                } else {
                    sendMessage(chatId, "Не удалось определить ваш номер телефона.");
                }
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(chatId, "Произошла ошибка при отмене заказа.");
        }
    }

    // Добавляем метод для завершения заказа
    private void completeOrder(long chatId, int orderId) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Обновляем статус в таблице orders
            String updateOrderQuery = "UPDATE orders SET status = 'Выполнен' WHERE order_id = ?";
            
            // Обновляем статус в таблице courier_orders
            String updateCourierOrderQuery = "UPDATE courier_orders SET order_status = 'Выполнен' WHERE order_id = ?";
            
            try (PreparedStatement orderStmt = connection.prepareStatement(updateOrderQuery);
                 PreparedStatement courierOrderStmt = connection.prepareStatement(updateCourierOrderQuery)) {
                
                connection.setAutoCommit(false);
                
                orderStmt.setInt(1, orderId);
                courierOrderStmt.setInt(1, orderId);
                
                int updatedOrders = orderStmt.executeUpdate();
                int updatedCourierOrders = courierOrderStmt.executeUpdate();
                
                if (updatedOrders > 0 && updatedCourierOrders > 0) {
                    connection.commit();
                    sendMessage(chatId, "✅ Заказ №" + orderId + " успешно завершен!");
                } else {
                    connection.rollback();
                    sendMessage(chatId, "❌ Не удалось завершить заказ. Возможно, он уже завершен или отменен.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(chatId, "Произошла ошибка при завершении заказа.");
        }
    }
}