package com.thomas.espdoorbell.doorbell.streaming.websocket.config

import com.thomas.espdoorbell.doorbell.streaming.websocket.handler.OutboundStreamHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter

@Configuration
class WebSocketConfig {

    @Bean
    fun webSocketHandlerAdapter(): WebSocketHandlerAdapter {
        return WebSocketHandlerAdapter()
    }

    @Bean
    fun webSocketHandlerMapping(
        outboundStreamHandler: OutboundStreamHandler
    ): HandlerMapping {
        val map = mapOf(
            "/ws/stream/outbound/{deviceId}" to outboundStreamHandler
        )
        
        return SimpleUrlHandlerMapping(map, 1)
    }
}
