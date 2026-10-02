package com.ktb10.kgb.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "spring.datasource.url=jdbc:h2:mem:prometheus-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PrometheusMetricsIntegrationTest {

    @LocalServerPort
    private int applicationPort;

    @LocalManagementPort
    private int managementPort;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void prometheusEndpointIsExposedOnlyOnManagementPort() {
        ResponseEntity<String> publicMetrics = restTemplate.getForEntity(
                endpoint(applicationPort, "/actuator/prometheus"), String.class);

        assertThat(managementPort).isNotEqualTo(applicationPort);
        assertThat(publicMetrics.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> health = restTemplate.getForEntity(
                endpoint(managementPort, "/actuator/health"), String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> metricsResponse = restTemplate.getForEntity(
                endpoint(managementPort, "/actuator/prometheus"), String.class);
        assertThat(metricsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(metricsResponse.getBody())
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

    private static String endpoint(int port, String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
