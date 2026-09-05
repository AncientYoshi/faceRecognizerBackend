package com.tuhmb.smartattendancebackend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityCorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void attendanceSessionCreatePreflightAllowsIdempotencyKey() throws Exception {
        mockMvc.perform(options("/attendance-sessions")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "authorization,content-type,idempotency-key"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:4200"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("idempotency-key")
                ));
    }
}
