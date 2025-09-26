# LuminaServer

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

基于Spring Boot的实时游戏服务器，支持WebSocket通信、Redis缓存、MySQL数据存储和WebRTC音视频通信。采用混合通信模型（TCP + WebRTC DataChannel），支持低延迟多人对战。

## 🚀 功能特性

- **混合通信模型**: TCP（MINA）处理登录/匹配/信令，WebRTC DataChannel处理高频P2P数据
- **实时通信**: 基于WebSocket的双向实时通信
- **房间管理**: 完整的游戏房间创建、加入、离开机制
- **智能匹配**: 支持多种匹配模式的玩家匹配系统
- **音视频通信**: 集成WebRTC支持语音和视频通话
- **服务器权威**: 关键逻辑服务器校验，防作弊机制
- **高性能**: 支持单服3k-10k并发连接，自定义二进制消息编解码器
- **数据持久化**: MySQL数据库存储用户和游戏数据
- **缓存系统**: Redis缓存提升性能
- **健康监控**: 完整的服务器健康检查和监控系统
- **高可用**: 断线重连，状态恢复

## 🛠 技术栈

- **后端框架**: Spring Boot 3.2.0
- **Java版本**: JDK 17
- **网络层**: Apache MINA 2.2.3
- **通信协议**: WebSocket, WebRTC
- **数据库**: MySQL 8.0+
- **缓存**: Redis 6.0+
- **消息编解码**: Protocol Buffers 3.25.1
- **监控**: Micrometer + Prometheus
- **日志**: SLF4J + Logback

## 📁 项目结构

```
LuminaServer/
├── src/main/java/com/whale/lumina/
│   ├── auth/                    # 认证模块
│   │   └── AuthService.java
│   ├── codec/                   # 消息编解码
│   │   └── MessageCodec.java
│   ├── common/                  # 通用组件
│   │   ├── ErrorCodes.java
│   │   └── GameException.java
│   ├── data/                    # 数据访问层
│   │   ├── DataSyncService.java
│   │   ├── MySQLClient.java
│   │   └── RedisClient.java
│   ├── gateway/                 # 网关层
│   │   └── NetServer.java
│   ├── matching/                # 匹配系统
│   │   └── MatchingService.java
│   ├── monitoring/              # 监控模块
│   │   └── HealthEndpoint.java
│   ├── room/                    # 房间管理
│   │   ├── Room.java
│   │   ├── RoomManager.java
│   │   └── RoomService.java
│   └── LuminaServerApplication.java
├── src/main/resources/
│   └── application.yml
├── docs/                        # 项目文档
│   ├── 项目需求文档.md
│   ├── 项目类说明文档.md
│   └── 游戏服务器开发文档_模块与类定义.md
└── pom.xml
```

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

### 安装步骤

1. **克隆项目**
   ```bash
   git clone https://github.com/netwindsky/LuminaServer.git
   cd LuminaServer
   ```

2. **配置数据库**
   ```bash
   # 创建MySQL数据库
   mysql -u root -p
   CREATE DATABASE lumina_game_dev;
   
   # 启动Redis服务
   redis-server
   ```

3. **配置应用**
   
   编辑 `src/main/resources/application.yml` 文件，配置数据库连接信息：
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/lumina_game_dev
       username: your_username
       password: your_password
     redis:
       host: localhost
       port: 6379
   ```

4. **编译运行**
   ```bash
   # 编译项目
   mvn clean compile
   
   # 运行服务器
   mvn spring-boot:run
   ```

5. **验证服务**
   
   访问健康检查端点：
   ```bash
   curl http://localhost:8080/health
   ```

## 📖 API文档

### WebSocket连接

- **连接地址**: `ws://localhost:8080/game`
- **消息格式**: 二进制/JSON格式
- **支持的消息类型**:
  - 房间管理 (创建、加入、离开)
  - 玩家匹配
  - 游戏状态同步
  - WebRTC信令

### HTTP接口

- **健康检查**: `GET /health`
- **服务器状态**: `GET /status`

## 🏗 核心模块

### 房间管理系统
- 支持多种游戏模式
- 动态房间创建和销毁
- 玩家状态管理
- 房间设置配置

### 匹配系统
- 快速匹配
- 自定义匹配
- 技能匹配
- 地理位置匹配

### 数据存储
- MySQL持久化存储
- Redis缓存加速
- 数据同步服务
- 事务管理

### 监控系统
- 服务器健康检查
- 性能指标收集
- 错误日志记录
- 实时状态监控

## 🔧 配置说明

主要配置项在 `application.yml` 中：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/lumina_game_dev
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:password}
  
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}

lumina:
  game:
    max-rooms: 1000
    max-players-per-room: 8
    match-timeout: 30000
```

## 🤝 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📝 开发规范

- 遵循Java编码规范
- 使用驼峰命名法
- 添加适当的注释和文档
- 编写单元测试
- 提交前进行代码审查

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 📞 联系方式

- 项目维护者: netwindsky
- 项目地址: https://github.com/netwindsky/LuminaServer

## 🙏 致谢

感谢所有为这个项目做出贡献的开发者们！

---

⭐ 如果这个项目对你有帮助，请给它一个星标！