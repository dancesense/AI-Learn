# 游知领航

按墨刀参考页面复刻的 Java Web 全栈项目。前端使用 React + Vite，后端使用
Spring Boot 3、Spring JDBC 和 MySQL；生产构建时，前端会被打包进 Spring Boot
静态资源目录，因此最终只需要启动一个 Java 服务。

> 请仅在已获得原页面及素材使用授权的前提下使用本项目。

## 本地运行

环境要求：Java 21+、Maven 3.9+、Node.js 20+、MySQL 8+。

```bash
mysql -uroot -p -e \
  "CREATE DATABASE IF NOT EXISTS youzhi_linghang CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"

export DB_PASSWORD="<你的本机 MySQL 密码>"
cd frontend
npm install
cd ..
mvn spring-boot:run
```

打开 `http://localhost:8081/#/home`。

数据库表结构和演示数据会在第一次启动时自动初始化。默认连接参数为：

- 数据库：`youzhi_linghang`
- 用户名：`root`
- 密码：通过 `DB_PASSWORD` 环境变量提供

可通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 和 `SERVER_PORT` 环境变量覆盖。
也可以在安装 Docker 后设置 `MYSQL_ROOT_PASSWORD`，再执行
`docker compose up -d` 启动项目自带的 MySQL 8.4。请勿将真实密码提交到仓库。

## 前端独立开发

先启动 Java 后端，再执行：

```bash
cd frontend
npm run dev
```

开发预览默认地址为 `http://localhost:4173`，`/api` 请求会代理到
`http://localhost:8081`。

## 功能

- 首页、内容广场、家教服务、学霸社群、关于我们和个人中心
- 内容搜索、分类筛选、点赞和关注
- 导师科目、年级、价格筛选及预约提交
- 社群加入/退出
- 用户身份切换及个人中心数据展示
- 邮箱注册、登录、退出和刷新后登录保持（密码使用 BCrypt 加密）
- 桌面端与移动端响应式布局

如需初始化体验账号 `demo@youzhi.com`，第一次启动前通过 `DEMO_PASSWORD`
环境变量设置它的密码；也可以直接在页面注册新账号。

## 验证

```bash
mvn test
cd frontend
npm run build
npm run test:sites
```

设计对照记录见 [design-qa.md](design-qa.md)。
