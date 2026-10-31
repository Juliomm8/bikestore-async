package com.bikestore.asyncapp.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bikestore.asyncapp.model.OrderRequest;

@WebMvcTest(OrderController.class)
class OrderControllerMvcTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderSubmissionService submissions;

    @Test
    void acceptsMultipleItemsAndReturnsOneIdAndAuthoritativeTotal() throws Exception {
        when(submissions.submit(any(OrderRequest.class)))
                .thenReturn(new OrderSubmissionResult("pedido-123", new BigDecimal("4170.00")));

        mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerEmail":"ana@example.com","items":[
                          {"bikeModel":"Alerce","quantity":2},
                          {"bikeModel":"Ruta Clara","quantity":1}
                        ]}
                        """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pedidoId").value("pedido-123"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.total").value(4170.0));

        verify(submissions).submit(any(OrderRequest.class));
    }

    @Test
    void rejectsInvalidQuantitiesBeforeSubmitting() throws Exception {
        mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerEmail":"ana@example.com","items":[
                          {"bikeModel":"Alerce","quantity":0}
                        ]}
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissions);
    }
}
