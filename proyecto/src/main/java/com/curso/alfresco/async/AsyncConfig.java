package com.curso.alfresco.async;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Leccion 11: activa @Async. Spring Boot aporta el pool de hilos (propiedades spring.task.execution.*).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
