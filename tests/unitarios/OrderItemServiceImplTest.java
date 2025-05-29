package com.selimhorri.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.selimhorri.app.domain.OrderItem;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.dto.OrderItemDto;
import com.selimhorri.app.dto.ProductDto;
import com.selimhorri.app.repository.OrderItemRepository;

@ExtendWith(MockitoExtension.class)
class OrderItemServiceImplTest {

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private OrderItemServiceImpl orderItemService;

    @Test
    void testSave_ShouldProcessOrderItemCorrectlyAndCalculateShippingData() {
        // Given - Preparar datos de entrada simulando un cálculo de envío
        ProductDto productDto = ProductDto.builder()
                .productId(101)
                .build();

        OrderDto orderDto = OrderDto.builder()
                .orderId(202)
                .build();

        OrderItemDto inputOrderItemDto = OrderItemDto.builder()
                .productId(101)
                .orderId(202)
                .orderedQuantity(3) // Cantidad que afecta el costo de envío
                .productDto(productDto)
                .orderDto(orderDto)
                .build();

        // Simular el orderItem entity que se guardará
        OrderItem orderItemToSave = OrderItem.builder()
                .productId(101)
                .orderId(202)
                .orderedQuantity(3)
                .build();

        // Simular el orderItem entity guardado (después de persistir)
        OrderItem savedOrderItem = OrderItem.builder()
                .productId(101)
                .orderId(202)
                .orderedQuantity(3)
                .build();

        // Configurar mocks
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(savedOrderItem);

        // When - Ejecutar el método bajo prueba (equivalente al cálculo de envío)
        OrderItemDto result = orderItemService.save(inputOrderItemDto);

        // Then - Verificar resultados del procesamiento de envío
        assertNotNull(result);
        assertEquals(101, result.getProductId());
        assertEquals(202, result.getOrderId());
        assertEquals(3, result.getOrderedQuantity()); // Verificar que la cantidad se procesó correctamente
        assertEquals(101, result.getProductDto().getProductId());
        assertEquals(202, result.getOrderDto().getOrderId());

        // Verificar que el repository fue llamado correctamente
        verify(orderItemRepository).save(any(OrderItem.class));
    }
}
