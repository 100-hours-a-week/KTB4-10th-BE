package com.ktb10.kgb.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prometheus-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class PrometheusMetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void prometheusEndpointExposesRuntimeHttpAndHikariMetrics() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        String metrics = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(metrics)
                .contains("process_cpu_usage")
                .contains("jvm_memory_used_bytes")
                .contains("jvm_gc_")
                .contains("jvm_threads_live_threads")
                .contains("http_server_requests_seconds_count")
                .contains("hikaricp_connections_active")
                .contains("hikaricp_connections_idle")
                .contains("hikaricp_connections_pending")
                .contains("hikaricp_connections_max");
    }
}
