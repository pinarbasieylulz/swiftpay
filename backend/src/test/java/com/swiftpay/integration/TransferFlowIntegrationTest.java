package com.swiftpay.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.dto.LoginRequest;
import com.swiftpay.dto.TransferRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID ALICE_ACCOUNT = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");
    private static final String BOB_IBAN = "TR110006200000000022220002";

    private String loginAs(String username) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword("Passw0rd!");
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private TransferRequest transferRequest(UUID source, String iban, BigDecimal amount, String key) {
        TransferRequest r = new TransferRequest();
        r.setSourceAccountId(source);
        r.setDestinationIban(iban);
        r.setAmount(amount);
        r.setDescription("test");
        r.setIdempotencyKey(key);
        return r;
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("wrong");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutToken_returns401WithErrorModel() throws Exception {
        mockMvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("AUTH_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void protectedEndpoint_withInvalidToken_returns401WithErrorModel() throws Exception {
        mockMvc.perform(get("/api/accounts/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_ERROR"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void createTransfer_thenFetchById_thenListAndStats_succeed() throws Exception {
        String token = loginAs("alice");
        String idemKey = "it-" + UUID.randomUUID();

        String body = mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest(ALICE_ACCOUNT, BOB_IBAN, new BigDecimal("55.00"), idemKey))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        String transferId = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(get("/api/transfers/" + transferId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(55.00));

        mockMvc.perform(get("/api/transfers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());

        mockMvc.perform(get("/api/statistics/monthly").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void createTransfer_duplicateIdempotencyKey_returnsSameTransferOnce() throws Exception {
        String token = loginAs("alice");
        String idemKey = "it-" + UUID.randomUUID();
        TransferRequest req = transferRequest(ALICE_ACCOUNT, BOB_IBAN, new BigDecimal("10.00"), idemKey);

        String first = mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(first).get("id").asText();

        String second = mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondId = objectMapper.readTree(second).get("id").asText();

        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void createTransfer_insufficientBalance_returns422WithErrorModel() throws Exception {
        String token = loginAs("bob");
        TransferRequest req = transferRequest(
                UUID.fromString("aaaaaaaa-2222-2222-2222-222222222222"),
                "TR110006200000000011110001", new BigDecimal("999999.00"), "it-" + UUID.randomUUID());

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void createTransfer_invalidAmount_returns400ValidationError() throws Exception {
        String token = loginAs("alice");
        TransferRequest req = transferRequest(ALICE_ACCOUNT, BOB_IBAN, new BigDecimal("-5.00"), "it-" + UUID.randomUUID());

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void accessingAnotherCustomersAccount_isForbidden() throws Exception {
        String bobToken = loginAs("bob");
        TransferRequest req = transferRequest(ALICE_ACCOUNT, BOB_IBAN, new BigDecimal("10.00"), "it-" + UUID.randomUUID());

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void myAccountsAndLimits_returnOwnData() throws Exception {
        String token = loginAs("alice");
        mockMvc.perform(get("/api/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].iban").exists());

        mockMvc.perform(get("/api/accounts/me/limits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perTransferLimit").value(50000.00));
    }
}
