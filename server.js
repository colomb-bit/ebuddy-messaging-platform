const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const fs = require('fs');

const app = express();
app.use(cors());
app.use(bodyParser.json());

const DATA_FILE = 'database.json';

function loadData() {
  if (fs.existsSync(DATA_FILE)) {
    return JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
  }
  return { users: [], messages: [] };
}

function saveData(data) {
  fs.writeFileSync(DATA_FILE, JSON.stringify(data, null, 2));
}

// ثبت‌نام
app.post('/api/register', (req, res) => {
  const { username, password, device } = req.body;
  
  if (!username || !password) {
    return res.json({ success: false, message: 'نام‌کاربری یا رمز خالی است' });
  }
  
  let data = loadData();
  
  if (data.users.find(u => u.username === username)) {
    return res.json({ success: false, message: 'کاربر قبلاً وجود دارد' });
  }
  
  const newUser = {
    id: Date.now().toString(),
    username: username,
    password: password,
    device: device || 'unknown',
    createdAt: new Date()
  };
  
  data.users.push(newUser);
  saveData(data);
  
  res.json({ 
    success: true, 
    message: 'ثبت‌نام موفق',
    userId: newUser.id
  });
});

// لاگین
app.post('/api/login', (req, res) => {
  const { username, password } = req.body;
  
  let data = loadData();
  const user = data.users.find(u => u.username === username && u.password === password);
  
  if (!user) {
    return res.json({ success: false, message: 'نام‌کاربری یا رمز غلط' });
  }
  
  res.json({ 
    success: true, 
    message: 'لاگین موفق',
    user: {
      id: user.id,
      username: user.username,
      device: user.device
    }
  });
});

// دریافت لیست کاربران
app.get('/api/users', (req, res) => {
  let data = loadData();
  const users = data.users.map(u => ({
    id: u.id,
    username: u.username,
    device: u.device
  }));
  
  res.json({ success: true, users: users });
});

// ارسال پیام
app.post('/api/send-message', (req, res) => {
  const { fromId, toUsername, text } = req.body;
  
  if (!fromId || !toUsername || !text) {
    return res.json({ success: false, message: 'اطلاعات ناقص' });
  }
  
  let data = loadData();
  
  const toUser = data.users.find(u => u.username === toUsername);
  if (!toUser) {
    return res.json({ success: false, message: 'کاربر مقصد وجود ندارد' });
  }
  
  const newMessage = {
    id: Date.now().toString(),
    from: fromId,
    to: toUser.id,
    text: text,
    timestamp: new Date(),
    read: false
  };
  
  data.messages.push(newMessage);
  saveData(data);
  
  res.json({ 
    success: true, 
    message: 'پیام ارسال شد',
    messageId: newMessage.id
  });
});

// دریافت پیام‌های جدید
app.get('/api/messages/:userId', (req, res) => {
  const userId = req.params.userId;
  let data = loadData();
  
  const userMessages = data.messages
    .filter(m => m.to === userId && !m.read)
    .map(m => {
      const sender = data.users.find(u => u.id === m.from);
      return {
        id: m.id,
        from: sender ? sender.username : 'unknown',
        text: m.text,
        timestamp: m.timestamp
      };
    });
  
  data.messages.forEach(m => {
    if (m.to === userId) m.read = true;
  });
  saveData(data);
  
  res.json({ success: true, messages: userMessages });
});

// دریافت تمام پیام‌های کاربر
app.get('/api/all-messages/:userId', (req, res) => {
  const userId = req.params.userId;
  let data = loadData();
  
  const userMessages = data.messages
    .filter(m => m.from === userId || m.to === userId)
    .map(m => {
      const sender = data.users.find(u => u.id === m.from);
      const recipient = data.users.find(u => u.id === m.to);
      return {
        id: m.id,
        from: sender ? sender.username : 'unknown',
        to: recipient ? recipient.username : 'unknown',
        text: m.text,
        timestamp: m.timestamp
      };
    })
    .sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
  
  res.json({ success: true, messages: userMessages });
});

// ری‌ست داده‌ها
app.post('/api/reset', (req, res) => {
  const emptyData = { users: [], messages: [] };
  saveData(emptyData);
  res.json({ success: true, message: 'داده‌های ری‌ست شدند' });
});

// شروع سرور
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`سرور روی پورت ${PORT} فعال است`);
  console.log(`http://localhost:${PORT}`);
});
