package com.seocheon.fitian;

import java.io.FileInputStream;
import java.io.IOException;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
@MapperScan("com.seocheon.fitian.mapper")
@EnableScheduling
public class FitianApplication {

	@PostConstruct
	void initFirebase() throws IOException {
		try (var in = new FileInputStream(
				//"/home/ubuntu/serviceAccountKey.json"
	            "src/main/resources/serviceAccountKey.json"
				)) {

			FirebaseOptions opts = FirebaseOptions.builder()
	          .setCredentials(GoogleCredentials.fromStream(in))
	          .build();

			if (FirebaseApp.getApps().isEmpty()) {
				FirebaseApp.initializeApp(opts);
			}
	    }
	}
	
	public static void main(String[] args) {
		SpringApplication.run(FitianApplication.class, args);
	}
}
