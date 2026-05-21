const express = require('express');
const mysql = require('mysql2/promise');
const cors = require('cors');
require('dotenv').config();

const app = express();
app.use(cors());
app.use(express.json());

// Load configurations
const config = {
  host: process.env.DB_HOST || 'gz-cdb-3tcps32l.sql.tencentcdb.com',
  port: parseInt(process.env.DB_PORT || '25676'),
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '',
  database: process.env.DB_NAME || 'paotui_app',
  portLocal: parseInt(process.env.PORT || '8000')
};

console.log('🚀 Speed Run Backend Starting...');
console.log(`Connecting to Tencent CDB: ${config.host}:${config.port}, DB: ${config.database}`);

let pool = null;

// Self-healing database connection pool
async function getPool() {
  if (pool) return pool;
  
  try {
    pool = mysql.createPool({
      host: config.host,
      port: config.port,
      user: config.user,
      password: config.password,
      database: config.database,
      waitForConnections: true,
      connectionLimit: 10,
      queueLimit: 0,
      enableKeepAlive: true,
      keepAliveInitialDelay: 10000
    });
    
    // Quick ping to verify
    const connection = await pool.getConnection();
    console.log('✨ MySQL Database connected successfully!');
    connection.release();
    return pool;
  } catch (error) {
    console.error('❌ Database connection failed. Error details:', error.message);
    pool = null; // Reset
    throw error;
  }
}

// Global OTP memory cache (simulating SMS/Email sending)
const emailOtpCache = new Map();

// Helper to check table existence and column names to ensure dynamic schema safety
async function getTableColumns(tableName) {
  try {
    const dbPool = await getPool();
    const [rows] = await dbPool.query(`SHOW COLUMNS FROM \`${tableName}\``);
    return rows.map(r => r.Field);
  } catch (err) {
    return [];
  }
}

// Auto Table Initialization Check
async function initTables() {
  try {
    const dbPool = await getPool();

    // 1. User table (compatible with room structure)
    await dbPool.query(`
      CREATE TABLE IF NOT EXISTS \`user\` (
        \`email\` VARCHAR(191) PRIMARY KEY,
        \`nickname\` VARCHAR(100) NOT NULL,
        \`passwordHash\` VARCHAR(255) NOT NULL,
        \`createdAt\` BIGINT NOT NULL
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    `);

    // 2. Order table (with status, runner configuration)
    await dbPool.query(`
      CREATE TABLE IF NOT EXISTS \`order\` (
        \`id\` VARCHAR(50) PRIMARY KEY,
        \`userEmail\` VARCHAR(191) NOT NULL,
        \`type\` VARCHAR(20) NOT NULL,
        \`itemName\` VARCHAR(255) NOT NULL,
        \`notes\` TEXT,
        \`fromAddress\` VARCHAR(255) NOT NULL,
        \`toAddress\` VARCHAR(255) NOT NULL,
        \`tip\` DOUBLE DEFAULT 0.0,
        \`distance\` DOUBLE DEFAULT 1.0,
        \`status\` VARCHAR(30) DEFAULT 'SUBMITTED',
        \`runnerName\` VARCHAR(100) DEFAULT '',
        \`runnerPhone\` VARCHAR(50) DEFAULT '',
        \`createdAt\` BIGINT NOT NULL
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    `);

    // 3. Products table (for Seckills & general items)
    await dbPool.query(`
      CREATE TABLE IF NOT EXISTS \`product\` (
        \`id\` VARCHAR(50) PRIMARY KEY,
        \`name\` VARCHAR(255) NOT NULL,
        \`imageUrl\` VARCHAR(255) NOT NULL,
        \`originalPrice\` DOUBLE NOT NULL,
        \`seckillPrice\` DOUBLE NOT NULL,
        \`stockCount\` INT NOT NULL,
        \`totalStock\` INT NOT NULL,
        \`isSeckill\` TINYINT DEFAULT 1,
        \`startTimeMills\` BIGINT NOT NULL,
        \`endTimeMills\` BIGINT NOT NULL,
        \`category\` VARCHAR(50) NOT NULL,
        \`description\` TEXT
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    `);

    console.log('✅ Base MySQL tables verified and initialized.');

    // Seed initial products if 0 items exist
    const [rows] = await dbPool.query('SELECT COUNT(*) as count FROM `product`');
    if (rows[0].count === 0) {
      console.log('🌱 Seeding initial products & seckill items for Speed Run Errand app...');
      
      const now = Date.now();
      const oneHour = 3600000;
      
      const initialProducts = [
        [
          'prod_seckill_coffee',
          '【重磅秒杀】星巴克冰美式双人餐（少冰/中杯）',
          'https://images.unsplash.com/photo-1517701604599-bb29b565090c?auto=format&fit=crop&w=300&q=80',
          38.0, 9.90, 8, 20, 1,
          now - oneHour, now + oneHour * 2,
          'seckill', '经典极简双人美式派对装，提神醒脑，降真清爽。'
        ],
        [
          'prod_seckill_digital',
          '【闪电秒杀】10000mAh 磁吸极速充电宝',
          'https://images.unsplash.com/photo-1609592424109-dd9892f1b17c?auto=format&fit=crop&w=300&q=80',
          129.0, 19.90, 3, 10, 1,
          now - oneHour, now + oneHour * 4,
          'seckill', '超级速达专享，小巧超薄，磁吸快充不伤机。'
        ],
        [
          'prod_seckill_combo',
          '【深夜秒杀】尊享海陆空寿司豪华双人餐',
          'https://images.unsplash.com/photo-1579871494447-9811cf80d66c?auto=format&fit=crop&w=300&q=80',
          188.0, 39.90, 0, 15, 1,
          now - oneHour * 3, now - oneHour, // Expired Seckill for demo count
          'seckill', '日本名厨秘制酱汁，三文鱼、甜虾爆款，口口惊喜。'
        ],
        [
          'prod_fruit_cherry',
          '【自营专区】进口车厘子智利珍宝级JJ 500g',
          'https://images.unsplash.com/photo-1527661591475-527312dd65f5?auto=format&fit=crop&w=300&q=80',
          59.0, 59.0, 99, 100, 0,
          now, now + oneHour * 48,
          'food', '脆甜爆汁，自然熟透，颗颗饱满，冷链直达。'
        ],
        [
          'prod_digital_sd',
          '【极客精选】256GB 超高速TF闪存记忆卡',
          'https://images.unsplash.com/photo-1558244661-d248897f7bc4?auto=format&fit=crop&w=300&q=80',
          99.0, 99.0, 50, 50, 0,
          now, now + oneHour * 48,
          'digital', '读取150MB/s，写速超快，4K录制稳定不掉帧。'
        ]
      ];

      for (const prod of initialProducts) {
        await dbPool.query(`
          INSERT INTO \`product\` 
          (id, name, imageUrl, originalPrice, seckillPrice, stockCount, totalStock, isSeckill, startTimeMills, endTimeMills, category, description)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `, prod);
      }
      console.log('🌱 Successfully seeded 5 initial products into database!');
    }
  } catch (error) {
    console.error('⚠️ Table initialization failed. Please confirm database schema & rights.', error.message);
  }
}

// Verify tables on startup
setTimeout(initTables, 1000);


// ==================== API ENDPOINTS ====================

// Base Health Check
app.get('/', (req, res) => {
  res.json({
    appName: 'Speed Errand API Backend Service',
    status: 'ONLINE',
    version: '1.0.0',
    dbStatus: pool ? 'CONNECTED' : 'NOT_CONNECTED',
    author: 'AI Coding Coding Assistant'
  });
});

// 1. Send OTP verification code
app.post('/api/auth/send-code', (req, res) => {
  try {
    const { email } = req.body;
    if (!email || !email.includes('@')) {
      return res.status(400).json({
        code: 1001,
        success: false,
        message: '请输入合法的邮箱地址'
      });
    }

    const trimmedEmail = email.trim().toLowerCase();
    // Generate 4-digit code
    const optCode = Math.floor(1000 + Math.random() * 9000).toString();
    emailOtpCache.set(trimmedEmail, optCode);

    console.log(`[OTP] Sent to: ${trimmedEmail}, Code: ${optCode}`);

    res.json({
      code: 1000,
      success: true,
      message: `验证码已发送至 ${trimmedEmail}！【测试码: ${optCode}】`,
      data: optCode
    });
  } catch (error) {
    res.status(500).json({ code: 9999, success: false, message: error.message });
  }
});

// 2. Register Account
app.post('/api/auth/register', async (req, res) => {
  try {
    const { email, nickname, passwordHash, otpCode } = req.body;
    if (!email || !nickname || !passwordHash || !otpCode) {
      return res.status(400).json({
        code: 1001,
        success: false,
        message: '所有字段均不能为空'
      });
    }

    const trimmedEmail = email.trim().toLowerCase();
    const cachedCode = emailOtpCache.get(trimmedEmail);

    if (!cachedCode || cachedCode !== otpCode.trim()) {
      return res.status(400).json({
        code: 1001,
        success: false,
        message: '验证码错误或已失效！'
      });
    }

    const dbPool = await getPool();
    
    // Check if user already exists
    const [existing] = await dbPool.query('SELECT * FROM `user` WHERE `email` = ?', [trimmedEmail]);
    if (existing.length > 0) {
      return res.status(400).json({
        code: 1001,
        success: false,
        message: '该邮箱已被注册！'
      });
    }

    // Insert user
    const newUser = {
      email: trimmedEmail,
      nickname: nickname.trim(),
      passwordHash: passwordHash,
      createdAt: Date.now()
    };

    await dbPool.query(
      'INSERT INTO `user` (email, nickname, passwordHash, createdAt) VALUES (?, ?, ?, ?)',
      [newUser.email, newUser.nickname, newUser.passwordHash, newUser.createdAt]
    );

    // Clear OTP
    emailOtpCache.delete(trimmedEmail);

    console.log(`[AUTH] Registered new user: ${trimmedEmail}`);

    res.json({
      code: 1000,
      success: true,
      message: '邮箱注册成功！',
      data: newUser
    });
  } catch (error) {
    console.error('Register error:', error);
    res.status(500).json({ code: 9999, success: false, message: '注册异常: ' + error.message });
  }
});

// 3. Login User
app.post('/api/auth/login', async (req, res) => {
  try {
    const { email, passwordHash } = req.body;
    if (!email || !passwordHash) {
      return res.status(400).json({
        code: 1001,
        success: false,
        message: '账号和密码不能为空'
      });
    }

    const trimmedEmail = email.trim().toLowerCase();
    const dbPool = await getPool();

    const [users] = await dbPool.query('SELECT * FROM `user` WHERE `email` = ?', [trimmedEmail]);
    if (users.length === 0) {
      return res.status(400).json({
        code: 1002,
        success: false,
        message: '邮箱密码错误，登录失败'
      });
    }

    const user = users[0];
    if (user.passwordHash !== passwordHash) {
      return res.status(400).json({
        code: 1002,
        success: false,
        message: '用户名或密码错误，请查验！'
      });
    }

    res.json({
      code: 1000,
      success: true,
      message: '登录成功！',
      data: {
        email: user.email,
        nickname: user.nickname,
        passwordHash: user.passwordHash,
        createdAt: user.createdAt
      }
    });
  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({ code: 9999, success: false, message: '登录异常: ' + error.message });
  }
});

// 4. Get Products
app.get('/api/products', async (req, res) => {
  try {
    const dbPool = await getPool();
    const [products] = await dbPool.query('SELECT * FROM `product` ORDER BY `isSeckill` DESC, `id` ASC');
    
    // Map database result back to client-expected types
    const formattedProducts = products.map(prod => ({
      id: prod.id,
      name: prod.name,
      imageUrl: prod.imageUrl,
      originalPrice: Number(prod.originalPrice),
      seckillPrice: Number(prod.seckillPrice),
      stockCount: parseInt(prod.stockCount),
      totalStock: parseInt(prod.totalStock),
      isSeckill: prod.isSeckill === 1 || prod.isSeckill === true,
      startTimeMills: Number(prod.startTimeMills),
      endTimeMills: Number(prod.endTimeMills),
      category: prod.category,
      description: prod.description || ''
    }));

    res.json({
      code: 1000,
      success: true,
      message: '获取商品成功',
      data: formattedProducts
    });
  } catch (error) {
    console.error('Get products error:', error);
    res.status(500).json({ code: 9999, success: false, message: '获取商品列表异常: ' + error.message });
  }
});

// 5. Seckill Rush (High-fidelity Transaction)
app.post('/api/seckill/rush', async (req, res) => {
  const { productId, userEmail } = req.body;
  if (!productId || !userEmail) {
    return res.status(400).json({ code: 1001, success: false, message: '无效的秒杀请求' });
  }

  let dbConnection = null;
  try {
    const dbPool = await getPool();
    dbConnection = await dbPool.getConnection();

    // Start database transaction
    await dbConnection.beginTransaction();

    // 1. Fetch product with pessimistic lock (SELECT FOR UPDATE)
    const [products] = await dbConnection.query('SELECT * FROM `product` WHERE `id` = ? FOR UPDATE', [productId]);
    if (products.length === 0) {
      await dbConnection.rollback();
      return res.status(404).json({ code: 1001, success: false, message: '该秒杀商品不存在！' });
    }

    const product = products[0];

    // 2. Verify seckill conditions (time, stock)
    const now = Date.now();
    if (now < Number(product.startTimeMills)) {
      await dbConnection.rollback();
      return res.status(400).json({ code: 1001, success: false, message: '秒杀尚未开始，请耐心等待！' });
    }
    if (now > Number(product.endTimeMills)) {
      await dbConnection.rollback();
      return res.status(400).json({ code: 1001, success: false, message: '秒杀已结束！' });
    }
    if (parseInt(product.stockCount) <= 0) {
      await dbConnection.rollback();
      return res.status(409).json({ code: 1003, success: false, message: '对不起，抢购人数过多，商品已被瞬间秒杀一空！' });
    }

    // 3. User limit check (1 buy per user)
    const [existingOrders] = await dbConnection.query(
      'SELECT * FROM `order` WHERE `userEmail` = ? AND `itemName` LIKE ?',
      [userEmail, `%${product.name}%`]
    );
    if (existingOrders.length > 0) {
      await dbConnection.rollback();
      return res.status(400).json({
        code: 1001,
        success: false,
        message: '对不起，本商品每人限购1件，您已抢购成功，请前往订单查看！'
      });
    }

    // 4. Decrement Stock
    await dbConnection.query('UPDATE `product` SET `stockCount` = `stockCount` - 1 WHERE `id` = ?', [productId]);

    // 5. Generate dispatch order
    const orderId = `sk_ord_${Math.random().toString(36).substr(2, 8)}`;
    const newOrder = {
      id: orderId,
      userEmail: userEmail,
      type: 'BUY',
      itemName: `【秒杀抢购】${product.name}`,
      notes: `极速秒杀件！商品原价¥${product.originalPrice}，秒杀全包价¥${product.seckillPrice}！货款已付，请骑手即刻送达！`,
      fromAddress: '速达自营秒杀仓 (上海科技园北区)',
      toAddress: '您的收货地址 (系统就近定位)',
      tip: 15.0,
      distance: 3.2,
      status: 'SUBMITTED',
      runnerName: '',
      runnerPhone: '',
      createdAt: Date.now()
    };

    await dbConnection.query(
      `INSERT INTO \`order\` 
       (id, userEmail, type, itemName, notes, fromAddress, toAddress, tip, distance, status, runnerName, runnerPhone, createdAt)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)` ,
      [
        newOrder.id, newOrder.userEmail, newOrder.type, newOrder.itemName, 
        newOrder.notes, newOrder.fromAddress, newOrder.toAddress, 
        newOrder.tip, newOrder.distance, newOrder.status, 
        newOrder.runnerName, newOrder.runnerPhone, newOrder.createdAt
      ]
    );

    // Commit Transaction safely!
    await dbConnection.commit();
    console.log(`[SECKILL] Successful rush for product ${productId} by user ${userEmail}, OrderID: ${orderId}`);

    // Trigger full rider lifecycle simulation
    simulateRiderLifecycle(orderId);

    res.json({
      code: 1000,
      success: true,
      message: '秒杀成功！已为您自动安排最优跑腿骑手进行急速配送',
      data: newOrder
    });

  } catch (error) {
    console.error('Seckill rush error:', error);
    if (dbConnection) {
      try { await dbConnection.rollback(); } catch(e) {}
    }
    res.status(500).json({ code: 9999, success: false, message: '秒杀异常: ' + error.message });
  } finally {
    if (dbConnection) dbConnection.release();
  }
});

// 6. Create Standard Errand Order
app.post('/api/errand/create', async (req, res) => {
  try {
    const { userEmail, type, itemName, notes, fromAddress, toAddress, tip, distance } = req.body;
    if (!userEmail || !itemName || !fromAddress || !toAddress) {
      return res.status(400).json({
        code: 1001,
        success: false,
        message: '物品名称、取货/收货地址不能为空'
      });
    }

    const dbPool = await getPool();
    const orderId = `err_ord_${Math.random().toString(36).substr(2, 8)}`;
    const newOrder = {
      id: orderId,
      userEmail: userEmail,
      type: type || 'BUY',
      itemName: itemName,
      notes: notes || '',
      fromAddress: fromAddress,
      toAddress: toAddress,
      tip: parseFloat(tip || '0.0'),
      distance: parseFloat(distance || '1.0'),
      status: 'SUBMITTED',
      runnerName: '',
      runnerPhone: '',
      createdAt: Date.now()
    };

    await dbPool.query(
      `INSERT INTO \`order\` 
       (id, userEmail, type, itemName, notes, fromAddress, toAddress, tip, distance, status, runnerName, runnerPhone, createdAt)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)` ,
      [
        newOrder.id, newOrder.userEmail, newOrder.type, newOrder.itemName, 
        newOrder.notes, newOrder.fromAddress, newOrder.toAddress, 
        newOrder.tip, newOrder.distance, newOrder.status, 
        newOrder.runnerName, newOrder.runnerPhone, newOrder.createdAt
      ]
    );

    console.log(`[ORDER] Created errand order ${orderId} for ${userEmail}`);

    // Simulate Rider pick up and drive in background
    simulateRiderLifecycle(orderId);

    res.json({
      code: 1000,
      success: true,
      message: '跑腿订单提交成功！正在为您急速呼叫附近骑手...',
      data: newOrder
    });

  } catch (error) {
    console.error('Create order error:', error);
    res.status(500).json({ code: 9999, success: false, message: '创建跑腿订单异常: ' + error.message });
  }
});

// 7. Get user orders list
app.get('/api/errand/orders', async (req, res) => {
  try {
    const email = req.query.email || '';
    if (!email) {
      return res.status(400).json({ code: 1001, success: false, message: '邮箱地址参数缺失' });
    }

    const dbPool = await getPool();
    const [orders] = await dbPool.query(
      'SELECT * FROM `order` WHERE `userEmail` = ? ORDER BY `createdAt` DESC',
      [email.trim().toLowerCase()]
    );

    // Map properties back cleanly
    const formattedOrders = orders.map(ord => ({
      id: ord.id,
      userEmail: ord.userEmail,
      type: ord.type,
      itemName: ord.itemName,
      notes: ord.notes || '',
      fromAddress: ord.fromAddress,
      toAddress: ord.toAddress,
      tip: Number(ord.tip),
      distance: Number(ord.distance),
      status: ord.status,
      runnerName: ord.runnerName || '',
      runnerPhone: ord.runnerPhone || '',
      createdAt: Number(ord.createdAt)
    }));

    res.json({
      code: 1000,
      success: true,
      message: '订单加载完成',
      data: formattedOrders
    });
  } catch (error) {
    console.error('Get orders error:', error);
    res.status(500).json({ code: 9999, success: false, message: '获取订单列表异常: ' + error.message });
  }
});


// ==================== BACKGROUND SIMULATOR ====================

/**
 * Automatically advances order stages in database to mimic a living network of riders!
 * SUBMITTED -> ASSIGNED (5s) -> OUT_FOR_DELIVERY (15s) -> COMPLETED (25s)
 */
function simulateRiderLifecycle(orderId) {
  const riders = [
    { name: '美团跑腿 邓建华', phone: '13917482931' },
    { name: '顺丰同城 朱俊杰', phone: '18621554902' },
    { name: 'UU跑腿 李大志', phone: '15549881023' },
    { name: '速达极锋 王晓峰', phone: '13811224567' }
  ];

  setTimeout(async () => {
    try {
      const dbPool = await getPool();
      const [rows] = await dbPool.query('SELECT * FROM `order` WHERE `id` = ?', [orderId]);
      if (rows.length > 0 && rows[0].status === 'SUBMITTED') {
        const selectedRider = riders[Math.floor(Math.random() * riders.length)];
        await dbPool.query(
          'UPDATE `order` SET `status` = ?, `runnerName` = ?, `runnerPhone` = ? WHERE `id` = ?',
          ['ASSIGNED', selectedRider.name, selectedRider.phone, orderId]
        );
        console.log(`[Rider Sim] Order ${orderId} assigned to ${selectedRider.name}`);
      }
    } catch (e) {
      console.error('Rider auto assignment fail:', e.message);
    }
  }, 5000);

  setTimeout(async () => {
    try {
      const dbPool = await getPool();
      const [rows] = await dbPool.query('SELECT * FROM `order` WHERE `id` = ?', [orderId]);
      if (rows.length > 0 && rows[0].status === 'ASSIGNED') {
        await dbPool.query('UPDATE `order` SET `status` = ? WHERE `id` = ?', ['OUT_FOR_DELIVERY', orderId]);
        console.log(`[Rider Sim] Order ${orderId} is now OUT_FOR_DELIVERY`);
      }
    } catch (e) {
      console.error('Rider status transition fail:', e.message);
    }
  }, 20000);

  setTimeout(async () => {
    try {
      const dbPool = await getPool();
      const [rows] = await dbPool.query('SELECT * FROM `order` WHERE `id` = ?', [orderId]);
      if (rows.length > 0 && rows[0].status === 'OUT_FOR_DELIVERY') {
        await dbPool.query('UPDATE `order` SET `status` = ? WHERE `id` = ?', ['COMPLETED', orderId]);
        console.log(`[Rider Sim] Order ${orderId} is now COMPLETED`);
      }
    } catch (e) {
      console.error('Rider status completion fail:', e.message);
    }
  }, 45000);
}


// Start Listener
app.listen(config.portLocal, '0.0.0.0', () => {
  console.log(`✨ Express Server listening on port ${config.portLocal} at http://0.0.0.0:${config.portLocal}`);
  console.log('Connect your Android Client to: http://your-vps-ip:8000');
});
