package com.tierlist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.io.IOException;

@SpringBootApplication
public class TierlistBackend {
    public static void main(String[] args) throws IOException {
        SpringApplication.run(TierlistBackend.class, args);
        System.out.println("http://localhost:8080/");
        System.out.println("http://localhost:8080/test");
        if (System.getProperty("os.name").toLowerCase().contains("win")
                && System.getenv("DOCKER_CONTAINER") == null) {
            try {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler http://localhost:8080/");
            } catch (IOException e) {
                System.err.println("Не удалось открыть браузер: " + e.getMessage());
            }
        }
    }
}