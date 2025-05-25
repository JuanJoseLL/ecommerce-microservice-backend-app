package com.selimhorri.app.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.selimhorri.app.domain.Cart;
import com.selimhorri.app.domain.Order;
import com.selimhorri.app.dto.CartDto;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void testSave_ShouldCalculateAndSaveOrderCorrectly() {
        // Given
        CartDto cartDto = CartDto.builder()
                .cartId(1)
                .userId(123)
                .build();

        OrderDto inputOrderDto = OrderDto.builder()
                .orderDate(LocalDateTime.of(2025, 5, 25, 10, 30))
                .orderDesc("Test order for electronics")
                .orderFee(1299.99) // Precio total calculado
                .cartDto(cartDto)
                .build();

        // Simular la entidad Order que se guardará
        Cart savedCart = Cart.builder()
                .cartId(1)
                .userId(123)
                .build();

        Order savedOrder = Order.builder()
                .orderId(1)
                .orderDate(LocalDateTime.of(2025, 5, 25, 10, 30))
                .orderDesc("Test order for electronics")
                .orderFee(1299.99)
                .cart(savedCart)
                .build();

        // When
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderDto result = orderService.save(inputOrderDto);

        // Then
        assertNotNull(result, "El resultado no debería ser null");
        assertEquals(1, result.getOrderId(), "El ID de la orden debería ser 1");
        assertEquals(LocalDateTime.of(2025, 5, 25, 10, 30), result.getOrderDate(), "La fecha de orden debería coincidir");
        assertEquals("Test order for electronics", result.getOrderDesc(), "La descripción debería coincidir");
        assertEquals(1299.99, result.getOrderFee(), "El precio total debería ser 1299.99");
        
        // Verificar cart asociado
        assertNotNull(result.getCartDto(), "El cart no debería ser null");
        assertEquals(1, result.getCartDto().getCartId(), "El ID del cart debería ser 1");

        // Verificar que el repository fue llamado una vez
        verify(orderRepository, times(1)).save(any(Order.class));
    }
}
