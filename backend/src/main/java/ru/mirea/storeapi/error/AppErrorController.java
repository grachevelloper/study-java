package ru.mirea.storeapi.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.storeapi.dto.ApiError;

@RestController
public class AppErrorController implements ErrorController {
  @RequestMapping("/error")
  public ResponseEntity<ApiError> error(HttpServletRequest request) {
    Object originalUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
    Object originalStatus = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    int status = originalStatus instanceof Integer value ? value : 500;
    HttpStatus standard = HttpStatus.resolve(status);
    var body =
        new ApiError(
            Instant.now(),
            status,
            standard == null ? "HTTP_ERROR" : standard.name(),
            status >= 500 ? "Внутренняя ошибка сервера" : "Не удалось выполнить запрос",
            originalUri == null ? request.getRequestURI() : originalUri.toString(),
            MDC.get("correlationId"),
            Map.of());
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .cacheControl(CacheControl.noStore())
        .body(body);
  }
}
