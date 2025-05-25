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

import com.selimhorri.app.domain.Payment;
import com.selimhorri.app.domain.PaymentStatus;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.dto.PaymentDto;
import com.selimhorri.app.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void testSave_ShouldProcessPaymentCorrectlyAndReturnExpectedResult() {
        // Given - Preparar datos de entrada
        OrderDto orderDto = OrderDto.builder()
                .orderId(123)
                .build();

        PaymentDto inputPaymentDto = PaymentDto.builder()
                .paymentId(null) // Nuevo payment, sin ID
                .isPayed(true)
                .paymentStatus(PaymentStatus.COMPLETED)
                .orderDto(orderDto)
                .build();

        // Simular el payment entity que se guardará
        Payment paymentToSave = Payment.builder()
                .paymentId(null)
                .orderId(123)
                .isPayed(true)
                .paymentStatus(PaymentStatus.COMPLETED)
                .build();

        // Simular el payment entity guardado (con ID generado)
        Payment savedPayment = Payment.builder()
                .paymentId(456)
                .orderId(123)
                .isPayed(true)
                .paymentStatus(PaymentStatus.COMPLETED)
                .build();

        // Configurar mocks
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        // When - Ejecutar el método bajo prueba
        PaymentDto result = paymentService.save(inputPaymentDto);

        // Then - Verificar resultados
        assertNotNull(result);
        assertEquals(456, result.getPaymentId());
        assertEquals(true, result.getIsPayed());
        assertEquals(PaymentStatus.COMPLETED, result.getPaymentStatus());
        assertEquals(123, result.getOrderDto().getOrderId());

        // Verificar que el repository fue llamado correctamente
        verify(paymentRepository).save(any(Payment.class));
    }
}
