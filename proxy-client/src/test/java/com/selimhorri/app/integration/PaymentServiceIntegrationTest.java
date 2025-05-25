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

import com.selimhorri.app.business.payment.model.OrderDto;
import com.selimhorri.app.business.payment.model.PaymentDto;
import com.selimhorri.app.business.payment.model.PaymentStatus;
import com.selimhorri.app.business.payment.service.PaymentClientService;

/**
 * Integration test for Payment Service through proxy-client.
 * Tests the communication between proxy-client and payment-service via Feign client.
 * The payment service manages PaymentDto entities that represent payment processing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
public class PaymentServiceIntegrationTest {

    @MockBean
    private PaymentClientService paymentClientService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGetPaymentById_ShouldCallPaymentServiceAndReturnPaymentDto() throws Exception {
        // Given - Prepare test data for payment processing
        String paymentId = "1";
        
        OrderDto orderDto = OrderDto.builder()
                .orderId(1)
                .orderDesc("Test order for payment processing")
                .orderFee(799.99)
                .build();

        PaymentDto expectedPaymentDto = PaymentDto.builder()
                .paymentId(1)
                .isPayed(true)
                .paymentStatus(PaymentStatus.COMPLETED)
                .orderDto(orderDto)
                .build();

        // Mock the Feign client response
        ResponseEntity<PaymentDto> feignResponse = new ResponseEntity<>(expectedPaymentDto, HttpStatus.OK);
        when(paymentClientService.findById(eq(paymentId))).thenReturn(feignResponse);

        // When & Then - Execute request and verify response
        mockMvc.perform(get("/api/payments/{paymentId}", paymentId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(1))
                .andExpect(jsonPath("$.isPayed").value(true))
                .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.order.orderId").value(1))
                .andExpect(jsonPath("$.order.orderDesc").value("Test order for payment processing"))
                .andExpect(jsonPath("$.order.orderFee").value(799.99));

        // Verify that the Feign client was called correctly
        verify(paymentClientService).findById(eq(paymentId));
    }
}
