package br.com.reservas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// EnableScheduling (F5-2): job de expiração de pendentes (RN-31,
// ReservationExpirationJob); desligável por propriedade em teste.
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ReservasApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservasApplication.class, args);
    }
}
