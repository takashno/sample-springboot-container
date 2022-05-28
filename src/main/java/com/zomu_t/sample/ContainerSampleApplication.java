package com.zomu_t.sample;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class ContainerSampleApplication {

//	@Bean
//	public GcLogger gcLogger() {
//		return new GcLogger();
//	}

//	@EventListener
//	public void onReady(ApplicationReadyEvent event){
//        GcLogger gcLogger = new GcLogger();
//		gcLogger.start(null);
//	}

	public static void main(String[] args) {
		SpringApplication.run(ContainerSampleApplication.class, args);
	}

}
