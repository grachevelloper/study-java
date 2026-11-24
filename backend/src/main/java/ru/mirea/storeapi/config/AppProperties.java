package ru.mirea.storeapi.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid Jwt jwt, @Valid Bootstrap bootstrap,
    @Valid Cors cors, boolean cookieSecure, @Valid ImportLimits importLimits) {

    public record Jwt(@NotBlank String secretBase64, @NotBlank String issuer,
        @Min(1) long accessMinutes, @Min(1) long refreshDays) {
    }

    public record Bootstrap(String email, String name, String password) {
    }

    public record Cors(String allowedOrigins) {
        public List<String> asList() {
            if (allowedOrigins == null || allowedOrigins.isBlank()) {
                return List.of();
            }
            return Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                .filter(value -> !value.isBlank()).toList();
        }
    }

    public record ImportLimits(@Min(1) int maxRows) {
    }
}
