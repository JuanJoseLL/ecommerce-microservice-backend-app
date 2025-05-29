package com.selimhorri.app.integration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.selimhorri.app.business.order.model.CartDto;
import com.selimhorri.app.business.order.model.OrderDto;
import com.selimhorri.app.business.order.service.OrderClientService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
public class OrderServiceIntegrationTest {

    @MockBean
    private OrderClientService orderClientService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGetOrderById_ShouldCallOrderServiceAndReturnOrderDto() throws Exception {
        // Given - Prepare test data
        String orderId = "1";
        
        CartDto cartDto = CartDto.builder()
                .cartId(1)
                .userId(123)
                .build();

        OrderDto expectedOrderDto = OrderDto.builder()
                .orderId(1)
                .orderDate(LocalDateTime.of(2025, 5, 25, 10, 30))
                .orderDesc("Test order for electronics")
                .orderFee(1299.99)
                .cartDto(cartDto)
                .build();

        // Mock the Feign client response
        ResponseEntity<OrderDto> feignResponse = new ResponseEntity<>(expectedOrderDto, HttpStatus.OK);
        when(orderClientService.findById(eq(orderId))).thenReturn(feignResponse);

        // When & Then - Execute request and verify response
        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.orderDesc").value("Test order for electronics"))
                .andExpect(jsonPath("$.orderFee").value(1299.99))
                .andExpect(jsonPath("$.cart.cartId").value(1))
                .andExpect(jsonPath("$.cart.userId").value(123));

        // Verify that the Feign client was called correctly
        verify(orderClientService).findById(orderId);
    }
}
