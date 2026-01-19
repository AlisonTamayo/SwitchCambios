package com.bancario.compensacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class CompensacionApplication {

	public static void main(String[] args) {
		SpringApplication.run(CompensacionApplication.class, args);
	}

	@Bean
	public org.springframework.scheduling.TaskScheduler taskScheduler() {
		return new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
	}

}
