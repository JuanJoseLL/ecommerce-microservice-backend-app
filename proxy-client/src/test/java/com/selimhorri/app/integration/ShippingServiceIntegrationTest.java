package com.selimhorri.app.integration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.selimhorri.app.business.orderItem.model.OrderDto;
import com.selimhorri.app.business.orderItem.model.OrderItemDto;
import com.selimhorri.app.business.orderItem.model.OrderItemId;
import com.selimhorri.app.business.orderItem.model.ProductDto;
import com.selimhorri.app.business.orderItem.service.OrderItemClientService;

/**
 * Integration test for Shipping Service through proxy-client.
 * Tests the communication between proxy-client and shipping-service via Feign client.
 * The shipping service manages OrderItem entities that represent shipping calculations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
public class ShippingServiceIntegrationTest {

    @MockBean
    private OrderItemClientService orderItemClientService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGetShippingById_ShouldCallShippingServiceAndReturnOrderItemDto() throws Exception {
        // Given - Prepare test data for shipping calculation
        String orderId = "1";
        String productId = "101";
        
        ProductDto productDto = ProductDto.builder()
                .productId(101)
                .productTitle("Laptop Gaming")
                .build();

        OrderDto orderDto = OrderDto.builder()
                .orderId(1)
                .orderDesc("Test order for shipping")
                .build();

        OrderItemDto expectedOrderItemDto = OrderItemDto.builder()
                .productId(101)
                .orderId(1)
                .orderedQuantity(2)
                .productDto(productDto)
                .orderDto(orderDto)
                .build();

        // Mock the Feign client response
        ResponseEntity<OrderItemDto> feignResponse = new ResponseEntity<>(expectedOrderItemDto, HttpStatus.OK);
        when(orderItemClientService.findById(eq(new OrderItemId(101, 1)))).thenReturn(feignResponse);

        // When & Then - Execute request and verify response
        mockMvc.perform(get("/api/shippings/{orderId}/{productId}", orderId, productId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(101))
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.orderedQuantity").value(2))
                .andExpect(jsonPath("$.product.productId").value(101))
                .andExpect(jsonPath("$.product.productTitle").value("Laptop Gaming"))
                .andExpect(jsonPath("$.order.orderId").value(1))
                .andExpect(jsonPath("$.order.orderDesc").value("Test order for shipping"));

        // Verify that the Feign client was called correctly
        verify(orderItemClientService).findById(eq(new OrderItemId(101, 1)));
    }
}
