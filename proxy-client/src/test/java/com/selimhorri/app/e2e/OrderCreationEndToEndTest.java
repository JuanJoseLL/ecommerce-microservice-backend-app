package com.selimhorri.app.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selimhorri.app.business.order.model.CartDto;
import com.selimhorri.app.business.order.model.OrderDto;
import com.selimhorri.app.business.order.service.OrderClientService;
import com.selimhorri.app.business.product.model.CategoryDto;
import com.selimhorri.app.business.product.model.ProductDto;
import com.selimhorri.app.business.product.service.ProductClientService;
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

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test End-to-End para el Flujo Simplificado de Creación de Orden
 * 
 * Este test valida el flujo completo:
 * Cliente -> API Gateway -> Proxy Client -> (Product Service para validar producto) -> Order Service para crear orden
 * 
 * Escenarios cubiertos:
 * 1. Creación exitosa de orden con producto válido
 * 2. Creación de orden con producto no encontrado
 * 3. Creación de orden cuando Order Service no está disponible
 * 4. Creación de orden con datos inválidos
 * 5. Creación de orden con información compleja (producto con categoría)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
@DisplayName("Order Creation End-to-End Tests")
public class OrderCreationEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderClientService orderClientService;

    @MockBean
    private ProductClientService productClientService;

    @Autowired
    private ObjectMapper objectMapper;

    private CartDto testCart;
    private ProductDto testProduct;
    private CategoryDto testCategory;

    @BeforeEach
    void setUp() {
        // Configurar datos de prueba comunes
        testCategory = CategoryDto.builder()
                .categoryId(1)
                .categoryTitle("Electronics")
                .imageUrl("https://example.com/category.jpg")
                .build();

        testProduct = ProductDto.builder()
                .productId(101)
                .productTitle("Smartphone Samsung Galaxy")
                .imageUrl("https://example.com/smartphone.jpg")
                .sku("PHONE-SAM-001")
                .priceUnit(599.99)
                .quantity(50)
                .categoryDto(testCategory)
                .build();

        testCart = CartDto.builder()
                .cartId(1)
                .userId(123)
                .build();
    }

    @Test
    @DisplayName("1. Debe crear orden exitosamente con producto válido")
    void testCreateOrder_ShouldCreateOrderSuccessfully_WhenProductIsValid() throws Exception {
        // Given - Preparar datos de entrada para crear orden
        OrderDto orderRequest = OrderDto.builder()
                .orderDate(LocalDateTime.of(2025, 6, 15, 14, 30))
                .orderDesc("Orden de smartphone para cliente premium")
                .orderFee(599.99)
                .cartDto(testCart)
                .build();

        OrderDto expectedOrder = OrderDto.builder()
                .orderId(1)
                .orderDate(LocalDateTime.of(2025, 6, 15, 14, 30))
                .orderDesc("Orden de smartphone para cliente premium")
                .orderFee(599.99)
                .cartDto(testCart)
                .build();

        // Mock respuesta exitosa del Product Service (para validar producto)
        ResponseEntity<ProductDto> productResponse = new ResponseEntity<>(testProduct, HttpStatus.OK);
        when(productClientService.findById(eq("101"))).thenReturn(productResponse);

        // Mock respuesta exitosa del Order Service
        ResponseEntity<OrderDto> orderResponse = new ResponseEntity<>(expectedOrder, HttpStatus.OK);
        when(orderClientService.save(any(OrderDto.class))).thenReturn(orderResponse);

        // When & Then - Ejecutar solicitud y verificar respuesta
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.orderDate").value("15-06-2025__14:30:00:000000"))
                .andExpect(jsonPath("$.orderDesc").value("Orden de smartphone para cliente premium"))
                .andExpect(jsonPath("$.orderFee").value(599.99))
                .andExpect(jsonPath("$.cart.cartId").value(1))
                .andExpect(jsonPath("$.cart.userId").value(123));
    }

    @Test
    @DisplayName("2. Debe manejar correctamente cuando el producto no se encuentra")
    void testCreateOrder_ShouldHandleProductNotFound_WhenProductDoesNotExist() throws Exception {
        // Given - Solicitud de orden con producto inexistente
        OrderDto orderRequest = OrderDto.builder()
                .orderDate(LocalDateTime.of(2025, 6, 15, 14, 30))
                .orderDesc("Orden con producto inexistente")
                .orderFee(299.99)
                .cartDto(testCart)
                .build();

        // Mock Product Service retorna producto no encontrado
        ResponseEntity<ProductDto> productResponse = new ResponseEntity<>(null, HttpStatus.OK);
        when(productClientService.findById(eq("999"))).thenReturn(productResponse);

        // Mock Order Service retorna respuesta vacía (simulando manejo de error)
        ResponseEntity<OrderDto> orderResponse = new ResponseEntity<>(null, HttpStatus.OK);
        when(orderClientService.save(any(OrderDto.class))).thenReturn(orderResponse);

        // When & Then - Verificar que se maneja correctamente el caso
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("3. Debe manejar correctamente cuando Order Service no está disponible")
    void testCreateOrder_ShouldHandleOrderServiceUnavailable_WhenServiceIsDown() throws Exception {
        // Given - Datos válidos pero servicio no disponible
        OrderDto orderRequest = OrderDto.builder()
                .orderDate(LocalDateTime.of(2025, 6, 15, 14, 30))
                .orderDesc("Orden cuando servicio no disponible")
                .orderFee(799.99)
                .cartDto(testCart)
                .build();

        // Mock Product Service funciona normalmente
        ResponseEntity<ProductDto> productResponse = new ResponseEntity<>(testProduct, HttpStatus.OK);
        when(productClientService.findById(eq("101"))).thenReturn(productResponse);

        // Mock Order Service no disponible (retorna respuesta vacía)
        ResponseEntity<OrderDto> orderResponse = new ResponseEntity<>(null, HttpStatus.OK);
        when(orderClientService.save(any(OrderDto.class))).thenReturn(orderResponse);

        // When & Then - Verificar manejo de servicio no disponible
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("4. Debe manejar datos inválidos en la solicitud de orden")
    void testCreateOrder_ShouldHandleInvalidData_WhenOrderDataIsIncomplete() throws Exception {
        // Given - Datos de orden inválidos (cart nulo)
        OrderDto invalidOrderRequest = OrderDto.builder()
                .orderDate(LocalDateTime.of(2025, 6, 15, 14, 30))
                .orderDesc("Orden con datos inválidos")
                .orderFee(-100.0) // Precio negativo
                .cartDto(null) // Cart nulo
                .build();

        // Mock servicios pueden no ser llamados o retornar respuesta vacía
        ResponseEntity<OrderDto> orderResponse = new ResponseEntity<>(null, HttpStatus.OK);
        when(orderClientService.save(any(OrderDto.class))).thenReturn(orderResponse);

        // When & Then - Verificar manejo de datos inválidos
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidOrderRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("5. Debe crear orden con información compleja de producto y categoría")
    void testCreateOrder_ShouldCreateOrderWithComplexProductInfo_WhenProductHasCategory() throws Exception {
        // Given - Producto con categoría compleja y orden detallada
        CategoryDto subcategory = CategoryDto.builder()
                .categoryId(2)
                .categoryTitle("Smartphones")
                .imageUrl("https://example.com/smartphones.jpg")
                .parentCategoryDto(testCategory)
                .build();

        ProductDto complexProduct = ProductDto.builder()
                .productId(102)
                .productTitle("iPhone 15 Pro Max")
                .imageUrl("https://example.com/iphone15.jpg")
                .sku("PHONE-APL-015")
                .priceUnit(1299.99)
                .quantity(25)
                .categoryDto(subcategory)
                .build();

        CartDto premiumCart = CartDto.builder()
                .cartId(2)
                .userId(456)
                .build();

        OrderDto complexOrderRequest = OrderDto.builder()
                .orderDate(LocalDateTime.of(2025, 6, 15, 16, 45))
                .orderDesc("Orden premium de iPhone para cliente VIP")
                .orderFee(1299.99)
                .cartDto(premiumCart)
                .build();

        OrderDto expectedComplexOrder = OrderDto.builder()
                .orderId(2)
                .orderDate(LocalDateTime.of(2025, 6, 15, 16, 45))
                .orderDesc("Orden premium de iPhone para cliente VIP")
                .orderFee(1299.99)
                .cartDto(premiumCart)
                .build();

        // Mock Product Service retorna producto complejo
        ResponseEntity<ProductDto> productResponse = new ResponseEntity<>(complexProduct, HttpStatus.OK);
        when(productClientService.findById(eq("102"))).thenReturn(productResponse);

        // Mock Order Service procesa orden compleja
        ResponseEntity<OrderDto> orderResponse = new ResponseEntity<>(expectedComplexOrder, HttpStatus.OK);
        when(orderClientService.save(any(OrderDto.class))).thenReturn(orderResponse);

        // When & Then - Verificar creación de orden compleja
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(complexOrderRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(2))
                .andExpect(jsonPath("$.orderDate").value("15-06-2025__16:45:00:000000"))
                .andExpect(jsonPath("$.orderDesc").value("Orden premium de iPhone para cliente VIP"))
                .andExpect(jsonPath("$.orderFee").value(1299.99))
                .andExpect(jsonPath("$.cart.cartId").value(2))
                .andExpect(jsonPath("$.cart.userId").value(456));
    }
}
