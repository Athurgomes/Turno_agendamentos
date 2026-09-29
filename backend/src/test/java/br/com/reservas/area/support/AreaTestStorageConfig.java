package br.com.reservas.area.support;

import br.com.reservas.shared.storage.FileStorage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class AreaTestStorageConfig {

    @Bean
    @Primary
    public FileStorage fileStorage() {
        return new InMemoryFileStorage();
    }
}
