package com.gameexpert.world.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.gameexpert.engine.EngineWarmup;
import com.gameexpert.world.WorldBaselineReadiness;
import com.gameexpert.world.dto.CreateWorldRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 고정 월드 서버가 빈 DB로 처음 뜨면 월드를 하나 만들고 스폰 주변을 데운다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FixedWorldBootstrap {

    static final String WORLD_NAME = "webcraft";

    private final WorldService worldService;
    private final WorldBaselineReadiness baselineReadiness;
    private final ObjectProvider<EngineWarmup> warmup;
    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void createWorldWhenEmpty() {
        if (!environment.getProperty("webcraft.worlds.fixed", Boolean.class, true)) return;
        Thread.ofVirtual().name("fixed-world-bootstrap").start(this::run);
    }

    private void run() {
        try {
            while (!baselineReadiness.isReady()) Thread.sleep(1_000L);
            if (!worldService.listWorlds().isEmpty()) return;
            worldService.createWorld(new CreateWorldRequest(WORLD_NAME));
            log.info("고정 월드를 만들었습니다: {}", WORLD_NAME);
            EngineWarmup engineWarmup = warmup.getIfAvailable();
            if (engineWarmup != null) engineWarmup.warmOnStartup();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException failure) {
            log.warn("고정 월드를 만들지 못했습니다", failure);
        }
    }
}
