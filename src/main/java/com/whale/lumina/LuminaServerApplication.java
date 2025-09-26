package com.whale.lumina;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Lumina游戏服务器主应用类
 * 
 * 基于Spring Boot的实时游戏服务器，支持混合通信模型：
 * - TCP/MINA：用于可靠的游戏状态同步和房间管理
 * - WebRTC DataChannel：用于低延迟的实时交互
 * 
 * @author Lumina Team
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class LuminaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LuminaServerApplication.class, args);
    }

}