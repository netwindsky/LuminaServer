package com.whale.lumina;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Lumina游戏服务器主启动类
 * 
 * 采用混合通信模型：TCP（MINA）+ WebRTC DataChannel
 * 支持P2P高频数据传输和服务器权威校验
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