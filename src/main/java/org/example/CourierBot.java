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
            if (update.hasMessage()) {
                long chatId = update.getMessage().getChatId();
                
                // Обработка команды /start
                if (update.getMessage().hasText() && update.getMessage().getText().equals("/start")) {
                    requestPhoneNumber(chatId);
                    return;
                }
                
                // Обработка получения контакта
                if (update.getMessage().hasContact()) {
                    String phoneNumber = update.getMessage().getContact().getPhoneNumber();
                    if (saveCourierPhoneNumber(phoneNumber, chatId)) {
                        sendMainMenu(chatId);
                    } else {
                        sendMessage(chatId, "❌ Доступ запрещен. Ваш номер телефона не найден в базе курьеров.");
                        requestPhoneNumber(chatId); // Предлагаем попробовать еще раз
                    }
                    return;
                }

                // Обработка текстовых команд
                if (update.getMessage().hasText()) {
                    String messageText = update.getMessage().getText();
                    String phoneNumber = getPhoneNumberByChatId(chatId);
                    
                    if (phoneNumber != null && isAuthorizedCourier(phoneNumber)) {
                        switch (messageText) {
                            case "Показать все доступные заказы":
                                sendAvailableOrders(chatId);
                                return;
                            case "Мои заказы":
                                sendMyOrders(chatId);
                                return;
                        }
                    } else {
                        sendMessage(chatId, "❌ Доступ запрещен. Пожалуйста, авторизуйтесь с помощью команды /start");
                    }
                }
            }

            // Обработка callback-запросов
            if (update.hasCallbackQuery()) {
                long chatId = update.getCallbackQuery().getMessage().getChatId();
                
                // Проверяем авторизацию перед выполнением действий
                if (!isAuthorizedCourier(String.valueOf(chatId))) {
                    sendMessage(chatId, "❌ Доступ запрещен. Пожалуйста, авторизуйтесь с помощью команды /start");
                    return;
                }

                String callbackData = update.getCallbackQuery().getData();
                
                // Отвечаем на callback
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
                }
            }
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

    // Метод для проверки и сохранения номера телефона курьера
    private boolean saveCourierPhoneNumber(String phoneNumber, long chatId) {
        // Форматируем номер телефона
        phoneNumber = formatPhoneNumber(phoneNumber);
        
        // Проверяем наличие курьера в базе
        String checkQuery = "SELECT phone_number FROM couriers WHERE phone_number = ?";
        String insertAuthQuery = "INSERT INTO courier_auth (chat_id, phone_number) VALUES (?, ?)";
        
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Проверяем существование курьера
            try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
                checkStmt.setString(1, phoneNumber);
                ResultSet rs = checkStmt.executeQuery();
                
                if (rs.next()) {
                    // Если курьер существует, сохраняем связь chat_id и номера телефона
                    try (PreparedStatement authStmt = connection.prepareStatement(insertAuthQuery)) {
                        authStmt.setLong(1, chatId);
                        authStmt.setString(2, phoneNumber);
                        authStmt.executeUpdate();
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
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
        // Сначала получаем номер телефона текущего курьера
        String phoneQuery = "SELECT phone_number FROM couriers WHERE phone_number = ?";
        String courierPhone = null;

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Получаем номер телефона курьера
            try (PreparedStatement phoneStmt = connection.prepareStatement(phoneQuery)) {
                phoneStmt.setString(1, String.valueOf(chatId));
                ResultSet phoneRs = phoneStmt.executeQuery();
                if (phoneRs.next()) {
                    courierPhone = phoneRs.getString("phone_number");
                }
            }

            if (courierPhone != null) {
                // Получаем заказы конкретного курьера
                String ordersQuery = 
                    "SELECT DISTINCT o.* FROM orders o " +
                    "INNER JOIN courier_orders co ON o.order_id = co.order_id " +
                    "WHERE co.order_status = 'В работе' " +
                    "AND co.courier_phone = ?";
                    
                try (PreparedStatement statement = connection.prepareStatement(ordersQuery)) {
                    statement.setString(1, courierPhone);
                    
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
                            sendMessage(chatId, "У вас пока нет активных заказов.");
                        }
                    }
                }
            } else {
                sendMessage(chatId, "Не удалось определить ваш номер телефона. Попробуйте перезапустить бота командой /start");
            }
        } catch (SQLException | TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Произошла ошибка при получении списка заказов: " + e.getMessage());
        }
    }

    // Метод для обработки взятия заказа курьером
    private void takeOrder(long chatId, int orderId) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            connection.setAutoCommit(false);
            try {
                // Получаем номер телефона курьера
                String phoneQuery = "SELECT phone_number FROM couriers WHERE phone_number = ?";
                String courierPhone = null;
                
                // Получаем номер телефона курьера
                try (PreparedStatement phoneStmt = connection.prepareStatement(phoneQuery)) {
                    phoneStmt.setString(1, String.valueOf(chatId));
                    ResultSet rs = phoneStmt.executeQuery();
                    if (rs.next()) {
                        courierPhone = rs.getString("phone_number");
                    }
                }
                
                if (courierPhone != null) {
                    // Проверяем, не взят ли уже заказ
                    String checkOrderQuery = "SELECT courier_phone FROM orders WHERE order_id = ? AND status = 'Принят'";
                    try (PreparedStatement checkStmt = connection.prepareStatement(checkOrderQuery)) {
                        checkStmt.setInt(1, orderId);
                        ResultSet rs = checkStmt.executeQuery();
                        
                        if (rs.next() && rs.getString("courier_phone") != null) {
                            connection.rollback();
                            sendMessage(chatId, "❌ Этот заказ уже взят другим курьером.");
                            return;
                        }
                    }

                    // Добавляем запись в courier_orders
                    String insertQuery = "INSERT INTO courier_orders (courier_phone, order_id, order_status) VALUES (?, ?, 'В работе')";
                    try (PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
                        insertStmt.setString(1, courierPhone);
                        insertStmt.setInt(2, orderId);
                        insertStmt.executeUpdate();
                    }

                    // Обновляем заказ
                    String updateQuery = "UPDATE orders SET status = 'В работе', courier_phone = ? WHERE order_id = ? AND status = 'Принят'";
                    try (PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {
                        updateStmt.setString(1, courierPhone);
                        updateStmt.setInt(2, orderId);
                        int updated = updateStmt.executeUpdate();
                        
                        if (updated > 0) {
                            connection.commit();
                            sendMessage(chatId, "✅ Заказ №" + orderId + " взят в работу!");
                        } else {
                            connection.rollback();
                            sendMessage(chatId, "❌ Не удалось взять заказ. Возможно, он уже занят.");
                        }
                    }
                } else {
                    sendMessage(chatId, "❌ Не удалось определить ваш номер телефона. Попробуйте перезапустить бота командой /start");
                }
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(chatId, "Произошла ошибка при взятии заказа.");
        }
    }

    // Метод для обработки отмены заказа курьером
    private void cancelOrder(long chatId, int orderId) {
        // Устанавливаем соединение с базой данных
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Отключаем автоматическое подтверждение транзакций для обеспечения целостности данных
            connection.setAutoCommit(false);
            try {
                // SQL-запрос для получения номера телефона курьера
                String phoneQuery = "SELECT phone_number FROM couriers LIMIT 1";
                String courierPhone = null;
                
                // Создаем и выполняем запрос для получения номера телефона
                try (PreparedStatement phoneStmt = connection.prepareStatement(phoneQuery)) {
                    // Выполняем запрос и получаем результат
                    ResultSet rs = phoneStmt.executeQuery();
                    // Если есть результат, сохраняем номер телефона
                    if (rs.next()) {
                        courierPhone = rs.getString("phone_number");
                    }
                }
                
                // Проверяем, что номер телефона был успешно получен
                if (courierPhone != null) {
                    // SQL-запрос для обновления статуса заказа в таблице orders
                    // Устанавливаем статус "Принят" и убираем привязку к курьеру
                    String updateOrderQuery = "UPDATE orders SET status = 'Принят', courier_phone = NULL " +
                        "WHERE order_id = ? AND courier_phone = ?";
                    
                    // SQL-запрос для обновления статуса в таблице courier_orders
                    // Отмечаем заказ как отмененный
                    String updateCourierOrderQuery = "UPDATE courier_orders SET order_status = 'Отменен' " +
                        "WHERE order_id = ? AND courier_phone = ?";
                    
                    // Создаем и выполняем оба запроса в рамках одной транзакции
                    try (PreparedStatement orderStmt = connection.prepareStatement(updateOrderQuery);
                         PreparedStatement courierOrderStmt = connection.prepareStatement(updateCourierOrderQuery)) {
                        
                        // Устанавливаем параметры для первого запроса
                        orderStmt.setInt(1, orderId);
                        orderStmt.setString(2, courierPhone);
                        // Устанавливаем параметры для второго запроса
                        courierOrderStmt.setInt(1, orderId);
                        courierOrderStmt.setString(2, courierPhone);
                        
                        // Выполняем запросы и получаем количество обновленных строк
                        int updatedOrders = orderStmt.executeUpdate();
                        int updatedCourierOrders = courierOrderStmt.executeUpdate();
                        
                        // Проверяем успешность выполнения обоих запросов
                        if (updatedOrders > 0 && updatedCourierOrders > 0) {
                            // Если оба запроса успешны, подтверждаем транзакцию
                            connection.commit();
                            // Отправляем сообщение об успешной отмене заказа
                            sendMessage(chatId, "❌ Заказ №" + orderId + " отменен.");
                        } else {
                            // Если хотя бы один запрос не выполнен, откатываем транзакцию
                            connection.rollback();
                            // Отправляем сообщение об ошибке
                            sendMessage(chatId, "Не удалось отменить заказ. Возможно, он вам не принадлежит.");
                        }
                    }
                } else {
                    // Отправляем сообщение, если не удалось определить номер телефона курьера
                    sendMessage(chatId, "Не удалось определить ваш номер телефона.");
                }
            } catch (SQLException e) {
                // В случае ошибки SQL откатываем транзакцию
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            // Обрабатываем возможные ошибки SQL
            e.printStackTrace();
            // Отправляем пользователю сообщение об ошибке
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

    // Добавляем метод проверки авторизации курьера
    private boolean isAuthorizedCourier(String phoneNumber) {
        try {
            // Форматируем номер
            System.out.println("Проверка авторизации для номера: " + phoneNumber);
            phoneNumber = formatPhoneNumber(phoneNumber);
            
            // Прямой запрос к базе данных для проверки
            String query = "SELECT * FROM couriers";
            try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = connection.prepareStatement(query)) {
                
                ResultSet rs = stmt.executeQuery();
                System.out.println("Все номера в базе данных:");
                while (rs.next()) {
                    String dbNumber = rs.getString("phone_number");
                    System.out.println("Номер в БД: " + dbNumber);
                    if (phoneNumber.equals(dbNumber)) {
                        System.out.println("Найдено совпадение!");
                        return true;
                    }
                }
                System.out.println("Совпадений не найдено");
                return false;
            }
        } catch (SQLException e) {
            System.out.println("Ошибка SQL: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Изменяем метод форматирования номера
    private String formatPhoneNumber(String phoneNumber) {
        System.out.println("Начало форматирования номера: " + phoneNumber);
        
        // Если номер null или пустой, возвращаем как есть
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            System.out.println("Получен пустой номер");
            return phoneNumber;
        }
        
        // Очищаем номер от всего кроме цифр и знака +
        phoneNumber = phoneNumber.replaceAll("[^0-9+]", "");
        System.out.println("После очистки: " + phoneNumber);
        
        // Если номер начинается с +, убираем его для дальнейшей обработки
        if (phoneNumber.startsWith("+")) {
            phoneNumber = phoneNumber.substring(1);
        }
        
        // Если номер начинается с 8, заменяем на 7
        if (phoneNumber.startsWith("8")) {
            phoneNumber = "7" + phoneNumber.substring(1);
        }
        
        // Если номер не начинается с 7, добавляем его
        if (!phoneNumber.startsWith("7")) {
            phoneNumber = "7" + phoneNumber;
        }
        
        // Добавляем + в начало номера
        phoneNumber = "+" + phoneNumber;
        
        System.out.println("Отформатированный номер: " + phoneNumber);
        return phoneNumber;
    }

    // Добавляем метод для получения номера телефона по chatId
    private String getPhoneNumberByChatId(long chatId) {
        String query = "SELECT phone_number FROM courier_auth WHERE chat_id = ? ORDER BY id DESC LIMIT 1";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = connection.prepareStatement(query)) {
            
            stmt.setLong(1, chatId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("phone_number");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}