package org.example.managesystem.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // 与前端约定一致：snake_case
            builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

            // java.util.Date（若有）格式
            builder.simpleDateFormat("yyyy-MM-dd HH:mm:ss");

            // Java 8 时间类型格式
            builder.serializers(
                    new LocalDateSerializer(DATE),
                    new LocalDateTimeSerializer(DATETIME)
            );
            builder.deserializers(
                    new LocalDateDeserializer(DATE),
                    new LocalDateTimeDeserializer(DATETIME)
            );
        };
    }
}
