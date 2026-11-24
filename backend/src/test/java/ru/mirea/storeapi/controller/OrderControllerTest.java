package ru.mirea.storeapi.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "EDITOR")
class OrderControllerTest {
    private static final String URGENT_ORDER = """
      {
        "externalId": "URGENT-1",
        "customerId": 1,
        "note": "Доставка в офис",
        "urgent": true,
        "tags": ["delivery"],
        "items": [{"productId": 1, "quantity": 2}]
      }
      """;

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate db;

    @BeforeEach
    void createCatalog() {
        db.update("""
        insert into customers (id, public_id, full_name, email, active, registered_at)
        values (1, random_uuid(), 'Анна Петрова', 'anna@example.org', true, current_timestamp)
        """);
        db.update("""
        insert into products (id, sku, name, price, stock_quantity, active)
        values (1, 'A-1', 'Лампа', 120.00, 5, true)
        """);
    }

    @Test
    void calculatesTotalAndPriorityFromOrderData() throws Exception {
        mvc.perform(post("/api/orders").contentType(APPLICATION_JSON)
            .content(URGENT_ORDER)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.order.total").value(240.0))
            .andExpect(jsonPath("$.order.priority").value("HIGH"))
            .andExpect(jsonPath("$.order.status").value("NEW"));
    }

    @Test
    void combinesTextSearchWithTagAndNumericFilters() throws Exception {
        mvc.perform(post("/api/orders").contentType(APPLICATION_JSON)
            .content(URGENT_ORDER)).andExpect(status().isCreated());

        mvc.perform(
            get("/api/orders").param("q", "лампа").param("tag", "DELIVERY")
                .param("total", "200").param("totalOp", "GT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/orders").param("q", "лампа").param("total", "250")
            .param("totalOp", "GT")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void returnsBadRequestWhenComparisonHasNoAmount() throws Exception {
        mvc.perform(get("/api/orders").param("totalOp", "GT"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
