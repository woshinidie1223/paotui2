# 速达跑腿 (Speed Errand) - Node.js Express 后端服务

这是一个基于 Node.js + Express + MySQL 开发的跑腿系统后端 API 服务，专门对接速达跑腿 App，负责连接您的腾讯云 CDB 云数据库，保护数据库密码不泄露在客户端中。

## 🌟 主要功能

- **安全认证模块：** 自带邮箱验证码 OTP 生成、注册、登录机制。
- **跑腿订单模块：** 创建帮我送、帮我买、帮我取订单。
- **高并发秒杀模块：** 基于 MySQL `SELECT ... FOR UPDATE` 排他锁事务处理，预防超卖与限购异常。
- **自动骑手调度：** 在后台自动模拟接单、配送中、已送达的状态流转流程，客户端可实现秒级刷新追踪。

---

## 🛠 宝塔面板部署指引

您的服务器 IP 为：`124.220.27.14`

### 1. 准备工作

在宝塔面板中的 **软件商店** 安装以下软件：
- **Node.js 版本管理器** (安装最新稳定的 Node.js，例如 v18+ 或 v20+)
- **PM2 管理器** 或 **Nginx** (用作反向代理)

### 2. 上传后端代码

1. 在服务器上创建一个目录，例如 `/www/wwwroot/paotui-backend`。
2. 将以下文件上传至该目录下：
   - `server.js`
   - `package.json`
   - `.env` (可以复制 `.env.example` 并重命名为 `.env`)

### 3. 配置数据库环境密码 `.env`

打开 `/www/wwwroot/paotui-backend/.env` 并填写您的真品配置：

```env
DB_HOST=gz-cdb-3tcps32l.sql.tencentcdb.com
DB_PORT=25676
DB_USER=root
DB_PASSWORD=您的腾讯云CDB数据库密码
DB_NAME=paotui_app
PORT=8000
```

### 4. 安装依赖并启动

在宝塔中打开命令行终端，进入该后端根目录并执行：

```bash
cd /www/wwwroot/paotui-backend
npm install
```

然后在宝塔的 **Node项目管理器**中：
1. **添加 Node 项目**：
   - 项目目录：`/www/wwwroot/paotui-backend`
   - 启动文件：`server.js`
   - 项目名称：`paotui-backend`
   - 端口：`8000`
2. 或者直接在终端使用 PM2 启动保持后台运行：
   ```bash
   npm install -g pm2
   pm2 start server.js --name "paotui-backend"
   pm2 save
   pm2 startup
   ```

### 5. 配置 Nginx 反代（可选，建议配置）

如果您希望客户端通过 `http://124.220.27.14` 直接调用，不用带 `:8000` 端口：
1. 宝塔面板 -> **网站** -> **添加站点** -> 输入域名或您的 IP `124.220.27.14`。
2. 进入该站点的设置 -> **反向代理** -> **添加反向代理**：
   - 代理名称：`paotui-api`
   - 目标URL：`http://127.0.0.1:8000`
3. 保存，这样您就可以通过 `http://124.220.27.14` 访问您的后端 API 了！
