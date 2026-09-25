# Chat Server - سیستم پیام‌رسان

## توضیح
سیستم پیام‌رسان برای Nokia C2-05 و Poco X3 Pro

## نصب
```bash
npm install
npm start
```

## API Endpoints

### ثبت‌نام
```
POST /api/register
Body: { username, password, device }
Response: { success, message, userId }
```

### لاگین
```
POST /api/login
Body: { username, password }
Response: { success, message, user }
```

### دریافت لیست کاربران
```
GET /api/users
Response: { success, users }
```

### ارسال پیام
```
POST /api/send-message
Body: { fromId, toUsername, text }
Response: { success, message, messageId }
```

### دریافت پیام‌های جدید
```
GET /api/messages/:userId
Response: { success, messages }
```

### دریافت تمام پیام‌های کاربر
```
GET /api/all-messages/:userId
Response: { success, messages }
```

### ری‌ست داده‌ها
```
POST /api/reset
Response: { success, message }
```

## توسعه‌دهنده
Claude AI

## پروژه
سیستم پیام‌رسان برای فناوری نهم
