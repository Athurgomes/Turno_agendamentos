package br.com.reservas.shared.web;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Serializa {@link LocalTime} como {@code "HH:mm"} em toda a API (docs/03, D-44), aceitando na
 * entrada tanto {@code HH:mm} quanto {@code HH:mm:ss} (ISO_LOCAL_TIME).
 */
@Configuration
public class JacksonTimeConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localTimeJacksonCustomizer() {
        return builder -> {
            builder.serializerByType(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm")));
            builder.deserializerByType(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ISO_LOCAL_TIME));
        };
    }
}
