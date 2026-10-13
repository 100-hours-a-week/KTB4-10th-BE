package com.ktb10.kgb.guidebook.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.guidebook.error.GuidebookErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

class GotenbergGuidebookPdfConverterTest {

    private static final String BASE_URL = "http://gotenberg.internal:3000";
    private static final byte[] PDF = "%PDF-1.7\nresult".getBytes();

    private MockRestServiceServer server;
    private SimpleMeterRegistry meterRegistry;
    private Semaphore semaphore;
    private GuidebookPdfProperties properties;
    private GotenbergGuidebookPdfConverter converter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        meterRegistry = new SimpleMeterRegistry();
        semaphore = new Semaphore(1);
        properties = new GuidebookPdfProperties(
                URI.create(BASE_URL),
                Duration.ofSeconds(1),
                Duration.ofSeconds(30),
                1,
                DataSize.ofMegabytes(20));
        converter = new GotenbergGuidebookPdfConverter(
                builder.build(),
                semaphore,
                properties,
                new GuidebookPdfMetrics(meterRegistry),
                Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void convertsHtmlWithPrintOptions() {
        server.expect(once(), requestTo(BASE_URL + "/forms/chromium/convert/html"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertThat(body).contains("name=\"files\"");
                    assertThat(body).contains("filename=\"index.html\"");
                    assertThat(body).contains("name=\"printBackground\"");
                    assertThat(body).contains("name=\"preferCssPageSize\"");
                    assertThat(body).contains("name=\"skipNetworkAlmostIdleEvent\"");
                    assertThat(body).contains("name=\"waitDelay\"");
                    assertThat(body).contains("1s");
                    assertThat(body).contains("https://tong.visitkorea.or.kr/image.jpg");
                })
                .andRespond(withSuccess(PDF, MediaType.APPLICATION_PDF));

        byte[] result = converter.convert(
                1L,
                "<html><img src=\"http://tong.visitkorea.or.kr/image.jpg\"></html>");

        assertThat(result).isEqualTo(PDF);
        assertThat(meterRegistry.find("kgb.guidebook.pdf.duration")
                .tag("result", "success").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.find("kgb.guidebook.pdf.result.size")
                .summary().totalAmount()).isEqualTo(PDF.length);
        server.verify();
    }

    @Test
    void convertsUpstreamErrorWithoutKeepingResponseBody() {
        server.expect(requestTo(BASE_URL + "/forms/chromium/convert/html"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("외부 변환기 응답 원문"));

        assertThatThrownBy(() -> converter.convert(1L, "<html>가이드북</html>"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.errorCode())
                            .isEqualTo(GuidebookErrorCode.PDF_EXPORT_FAILED);
                    assertThat(businessException).hasMessageNotContaining("외부 변환기 응답 원문");
                    assertThat(businessException.getCause())
                            .hasMessageNotContaining("외부 변환기 응답 원문");
                });
        server.verify();
    }

    @Test
    void distinguishesTimeout() {
        server.expect(requestTo(BASE_URL + "/forms/chromium/convert/html"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        assertThatThrownBy(() -> converter.convert(1L, "<html>가이드북</html>"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(GuidebookErrorCode.PDF_EXPORT_TIMEOUT);
        server.verify();
    }

    @Test
    void rejectsRequestWhenConcurrencyLimitIsExhausted() {
        semaphore.acquireUninterruptibly();

        assertThatThrownBy(() -> converter.convert(1L, "<html>가이드북</html>"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(GuidebookErrorCode.PDF_EXPORT_BUSY);
    }

    @Test
    void rejectsOversizedResult() {
        GuidebookPdfProperties smallLimit = new GuidebookPdfProperties(
                URI.create(BASE_URL),
                Duration.ofSeconds(1),
                Duration.ofSeconds(30),
                1,
                DataSize.ofBytes(PDF.length - 1));
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer smallLimitServer = MockRestServiceServer.bindTo(builder).build();
        converter = new GotenbergGuidebookPdfConverter(
                builder.build(),
                new Semaphore(1),
                smallLimit,
                new GuidebookPdfMetrics(meterRegistry),
                Clock.systemUTC());
        smallLimitServer.expect(requestTo(BASE_URL + "/forms/chromium/convert/html"))
                .andRespond(withSuccess(PDF, MediaType.APPLICATION_PDF));

        assertThatThrownBy(() -> converter.convert(1L, "<html>가이드북</html>"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(GuidebookErrorCode.PDF_EXPORT_TOO_LARGE);
        smallLimitServer.verify();
    }
}
