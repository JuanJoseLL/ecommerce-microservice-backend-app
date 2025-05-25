package com.selimhorri.app.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selimhorri.app.business.payment.model.PaymentDto;
import com.selimhorri.app.business.payment.model.PaymentStatus;
import com.selimhorri.app.business.payment.model.OrderDto;
import com.selimhorri.app.business.payment.service.PaymentClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test End-to-End para el Flujo de Pago de una Orden Existente
 * 
 * Este test valida el flujo completo:
 * Cliente -> API Gateway -> Proxy Client -> Payment Service para procesar el pago de una orden
 * 
 * Escenarios cubiertos:
 * 1. Procesamiento exitoso de pago para orden válida
 * 2. Intento de pago para orden no encontrada
 * 3. Procesamiento de pago cuando Payment Service no está disponible
 * 4. Procesamiento de pago con datos inválidos (orden sin ID)
 * 5. Procesamiento de pago con información compleja (orden con descripción y fee)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
public class PaymentProcessingEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentClientService paymentClientService;

    @Autowired
    private ObjectMapper objectMapper;

    private OrderDto validOrderDto;
    private PaymentDto paymentRequestDto;

    @BeforeEach
    void setUp() {
        // Orden válida para procesar pago
        validOrderDto = OrderDto.builder()
                .orderId(1)
                .orderDesc("Order for laptop purchase")
                .orderFee(1299.99)
                .build();

        // Payment request DTO para creación
        paymentRequestDto = PaymentDto.builder()
                .paymentId(null) // Nuevo pago, sin ID
                .isPayed(false)
                .paymentStatus(PaymentStatus.IN_PROGRESS)
                .orderDto(validOrderDto)
                .build();
    }

    @Test
    @DisplayName("Scenario 1: Successful payment processing for valid order")
    public void testPaymentProcessing_SuccessfulCreation_ShouldReturnPaymentDetails() throws Exception {
        // Given - Preparar respuesta exitosa del Payment Service
        PaymentDto createdPaymentDto = PaymentDto.builder()
                .paymentId(100)
                .isPayed(true)
                .paymentStatus(PaymentStatus.COMPLETED)
                .orderDto(validOrderDto)
                .build();

        ResponseEntity<PaymentDto> paymentServiceResponse = new ResponseEntity<>(createdPaymentDto, HttpStatus.OK);
        when(paymentClientService.save(any(PaymentDto.class))).thenReturn(paymentServiceResponse);

        // When & Then - Ejecutar request y verificar respuesta
        mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(100))
                .andExpect(jsonPath("$.isPayed").value(true))
                .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.order.orderId").value(1))
                .andExpect(jsonPath("$.order.orderDesc").value("Order for laptop purchase"))
                .andExpect(jsonPath("$.order.orderFee").value(1299.99));
    }

    @Test
    @DisplayName("Scenario 2: Payment processing for non-existent order")
    public void testPaymentProcessing_OrderNotFound_ShouldReturnEmptyResponse() throws Exception {
        // Given - Orden que no existe
        OrderDto nonExistentOrderDto = OrderDto.builder()
                .orderId(999)
                .orderDesc("Non-existent order")
                .orderFee(500.0)
                .build();

        PaymentDto paymentForNonExistentOrder = PaymentDto.builder()
                .paymentId(null)
                .isPayed(false)
                .paymentStatus(PaymentStatus.NOT_STARTED)
                .orderDto(nonExistentOrderDto)
                .build();

        // Payment Service retorna respuesta vacía (orden no encontrada)
        when(paymentClientService.save(any(PaymentDto.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.NOT_FOUND));

        // When & Then - Verificar que se maneja correctamente el error con respuesta vacía
        mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentForNonExistentOrder)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("Scenario 3: Payment processing when Payment Service is unavailable")
    public void testPaymentProcessing_ServiceUnavailable_ShouldReturnEmptyResponse() throws Exception {
        // Given - Payment Service no está disponible
        when(paymentClientService.save(any(PaymentDto.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.SERVICE_UNAVAILABLE));

        // When & Then - Verificar manejo de servicio no disponible con respuesta vacía
        mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequestDto)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("Scenario 4: Payment processing with invalid data (order without ID)")
    public void testPaymentProcessing_InvalidOrderData_ShouldReturnEmptyResponse() throws Exception {
        // Given - Orden sin ID (datos inválidos)
        OrderDto invalidOrderDto = OrderDto.builder()
                .orderId(null) // ID faltante
                .orderDesc("Invalid order without ID")
                .orderFee(200.0)
                .build();

        PaymentDto invalidPaymentRequest = PaymentDto.builder()
                .paymentId(null)
                .isPayed(false)
                .paymentStatus(PaymentStatus.NOT_STARTED)
                .orderDto(invalidOrderDto)
                .build();

        // Payment Service retorna respuesta vacía por datos inválidos
        when(paymentClientService.save(any(PaymentDto.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.BAD_REQUEST));

        // When & Then - Verificar manejo de datos inválidos con respuesta vacía
        mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidPaymentRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("Scenario 5: Payment processing with complex order information")
    public void testPaymentProcessing_ComplexOrderInfo_ShouldReturnCompletePaymentDetails() throws Exception {
        // Given - Orden con información compleja
        OrderDto complexOrderDto = OrderDto.builder()
                .orderId(42)
                .orderDesc("Premium gaming setup with multiple components - Graphics Card RTX 4090, CPU AMD Ryzen 9, 32GB RAM")
                .orderFee(3599.99)
                .build();

        PaymentDto complexPaymentRequest = PaymentDto.builder()
                .paymentId(null)
                .isPayed(false)
                .paymentStatus(PaymentStatus.IN_PROGRESS)
                .orderDto(complexOrderDto)
                .build();

        // Payment Service procesa exitosamente
        PaymentDto processedComplexPayment = PaymentDto.builder()
                .paymentId(200)
                .isPayed(true)
                .paymentStatus(PaymentStatus.COMPLETED)
                .orderDto(complexOrderDto)
                .build();

        ResponseEntity<PaymentDto> complexPaymentResponse = new ResponseEntity<>(processedComplexPayment, HttpStatus.OK);
        when(paymentClientService.save(any(PaymentDto.class))).thenReturn(complexPaymentResponse);

        // When & Then - Verificar procesamiento de orden compleja
        mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(complexPaymentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(200))
                .andExpect(jsonPath("$.isPayed").value(true))
                .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.order.orderId").value(42))
                .andExpect(jsonPath("$.order.orderDesc").value("Premium gaming setup with multiple components - Graphics Card RTX 4090, CPU AMD Ryzen 9, 32GB RAM"))
                .andExpect(jsonPath("$.order.orderFee").value(3599.99));
    }
}
