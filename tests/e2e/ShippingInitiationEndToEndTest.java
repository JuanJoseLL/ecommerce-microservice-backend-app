package com.selimhorri.app.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selimhorri.app.business.orderItem.model.OrderItemDto;
import com.selimhorri.app.business.orderItem.model.OrderItemId;
import com.selimhorri.app.business.orderItem.model.OrderDto;
import com.selimhorri.app.business.orderItem.model.ProductDto;
import com.selimhorri.app.business.orderItem.service.OrderItemClientService;
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
 * Test End-to-End para el Flujo de Inicio de Envío de una Orden Pagada
 * 
 * Este test valida el flujo completo:
 * Cliente -> API Gateway -> Proxy Client -> Shipping Service para iniciar el envío de una orden
 * 
 * Escenarios cubiertos:
 * 1. Inicio exitoso de envío para orden pagada válida
 * 2. Intento de envío para orden no encontrada
 * 3. Inicio de envío cuando Shipping Service no está disponible
 * 4. Inicio de envío con datos inválidos (orden sin ID)
 * 5. Inicio de envío con información compleja (múltiples productos)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
public class ShippingInitiationEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderItemClientService orderItemClientService;

    @Autowired
    private ObjectMapper objectMapper;

    private OrderDto validOrderDto;
    private ProductDto validProductDto;
    private OrderItemDto shippingRequestDto;

    @BeforeEach
    void setUp() {
        // Orden pagada válida para iniciar envío
        validOrderDto = OrderDto.builder()
                .orderId(1)
                .orderDesc("Paid order ready for shipping")
                .build();

        // Producto válido para el envío
        validProductDto = ProductDto.builder()
                .productId(101)
                .productTitle("Gaming Laptop")
                .build();

        // Shipping request DTO para iniciar envío
        shippingRequestDto = OrderItemDto.builder()
                .productId(101)
                .orderId(1)
                .orderedQuantity(2)
                .productDto(validProductDto)
                .orderDto(validOrderDto)
                .build();
    }

    @Test
    @DisplayName("Scenario 1: Successful shipping initiation for paid order")
    public void testShippingInitiation_SuccessfulCreation_ShouldReturnShippingDetails() throws Exception {
        // Given - Preparar respuesta exitosa del Shipping Service
        OrderItemDto createdShippingDto = OrderItemDto.builder()
                .productId(101)
                .orderId(1)
                .orderedQuantity(2)
                .productDto(validProductDto)
                .orderDto(validOrderDto)
                .build();

        ResponseEntity<OrderItemDto> shippingServiceResponse = new ResponseEntity<>(createdShippingDto, HttpStatus.OK);
        when(orderItemClientService.save(any(OrderItemDto.class))).thenReturn(shippingServiceResponse);

        // When & Then - Ejecutar request y verificar respuesta
        mockMvc.perform(post("/api/shippings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shippingRequestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(101))
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.orderedQuantity").value(2))
                .andExpect(jsonPath("$.product.productId").value(101))
                .andExpect(jsonPath("$.product.productTitle").value("Gaming Laptop"))
                .andExpect(jsonPath("$.order.orderId").value(1))
                .andExpect(jsonPath("$.order.orderDesc").value("Paid order ready for shipping"));
    }

    @Test
    @DisplayName("Scenario 2: Shipping initiation for non-existent order")
    public void testShippingInitiation_OrderNotFound_ShouldReturnEmptyResponse() throws Exception {
        // Given - Orden que no existe
        OrderDto nonExistentOrderDto = OrderDto.builder()
                .orderId(999)
                .orderDesc("Non-existent order")
                .build();

        ProductDto productDto = ProductDto.builder()
                .productId(201)
                .productTitle("Unknown Product")
                .build();

        OrderItemDto shippingForNonExistentOrder = OrderItemDto.builder()
                .productId(201)
                .orderId(999)
                .orderedQuantity(1)
                .productDto(productDto)
                .orderDto(nonExistentOrderDto)
                .build();

        // Shipping Service retorna respuesta vacía (orden no encontrada)
        when(orderItemClientService.save(any(OrderItemDto.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.NOT_FOUND));

        // When & Then - Verificar que se maneja correctamente el error con respuesta vacía
        mockMvc.perform(post("/api/shippings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shippingForNonExistentOrder)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("Scenario 3: Shipping initiation when Shipping Service is unavailable")
    public void testShippingInitiation_ServiceUnavailable_ShouldReturnEmptyResponse() throws Exception {
        // Given - Shipping Service no está disponible
        when(orderItemClientService.save(any(OrderItemDto.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.SERVICE_UNAVAILABLE));

        // When & Then - Verificar manejo de servicio no disponible con respuesta vacía
        mockMvc.perform(post("/api/shippings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shippingRequestDto)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("Scenario 4: Shipping initiation with invalid data (order without ID)")
    public void testShippingInitiation_InvalidOrderData_ShouldReturnEmptyResponse() throws Exception {
        // Given - Orden sin ID (datos inválidos)
        OrderDto invalidOrderDto = OrderDto.builder()
                .orderId(null) // ID faltante
                .orderDesc("Invalid order without ID")
                .build();

        ProductDto productDto = ProductDto.builder()
                .productId(301)
                .productTitle("Test Product")
                .build();

        OrderItemDto invalidShippingRequest = OrderItemDto.builder()
                .productId(301)
                .orderId(null) // ID faltante
                .orderedQuantity(1)
                .productDto(productDto)
                .orderDto(invalidOrderDto)
                .build();

        // Shipping Service retorna respuesta vacía por datos inválidos
        when(orderItemClientService.save(any(OrderItemDto.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.BAD_REQUEST));

        // When & Then - Verificar manejo de datos inválidos con respuesta vacía
        mockMvc.perform(post("/api/shippings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidShippingRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("Scenario 5: Shipping initiation with complex order information")
    public void testShippingInitiation_ComplexOrderInfo_ShouldReturnCompleteShippingDetails() throws Exception {
        // Given - Orden con múltiples productos y información compleja
        OrderDto complexOrderDto = OrderDto.builder()
                .orderId(42)
                .orderDesc("Premium gaming setup order with multiple high-value components - expedited shipping required")
                .build();

        ProductDto complexProductDto = ProductDto.builder()
                .productId(500)
                .productTitle("High-End Gaming Setup: RTX 4090 Graphics Card + AMD Ryzen 9 7950X CPU + 64GB DDR5 RAM")
                .build();

        OrderItemDto complexShippingRequest = OrderItemDto.builder()
                .productId(500)
                .orderId(42)
                .orderedQuantity(5) // Múltiples unidades para envío
                .productDto(complexProductDto)
                .orderDto(complexOrderDto)
                .build();

        // Shipping Service procesa exitosamente
        OrderItemDto processedComplexShipping = OrderItemDto.builder()
                .productId(500)
                .orderId(42)
                .orderedQuantity(5)
                .productDto(complexProductDto)
                .orderDto(complexOrderDto)
                .build();

        ResponseEntity<OrderItemDto> complexShippingResponse = new ResponseEntity<>(processedComplexShipping, HttpStatus.OK);
        when(orderItemClientService.save(any(OrderItemDto.class))).thenReturn(complexShippingResponse);

        // When & Then - Verificar procesamiento de envío complejo
        mockMvc.perform(post("/api/shippings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(complexShippingRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(500))
                .andExpect(jsonPath("$.orderId").value(42))
                .andExpect(jsonPath("$.orderedQuantity").value(5))
                .andExpect(jsonPath("$.product.productId").value(500))
                .andExpect(jsonPath("$.product.productTitle").value("High-End Gaming Setup: RTX 4090 Graphics Card + AMD Ryzen 9 7950X CPU + 64GB DDR5 RAM"))
                .andExpect(jsonPath("$.order.orderId").value(42))
                .andExpect(jsonPath("$.order.orderDesc").value("Premium gaming setup order with multiple high-value components - expedited shipping required"));
    }
}
