package com.ktb10.kgb.guidebook.pdf;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.guidebook.error.GuidebookErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Gotenberg의 격리된 Headless Chromium으로 HTML을 인쇄용 PDF로 변환합니다. */
@Component
public class GotenbergGuidebookPdfConverter implements GuidebookPdfConverter {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GotenbergGuidebookPdfConverter.class);
    private static final String CONVERSION_PATH = "/forms/chromium/convert/html";
    private static final String PDF_SIGNATURE = "%PDF-";

    private final RestClient restClient;
    private final Semaphore concurrencyLimit;
    private final long maxResultBytes;
    private final GuidebookPdfMetrics metrics;
    private final Clock clock;

    public GotenbergGuidebookPdfConverter(
            RestClient guidebookPdfRestClient,
            Semaphore guidebookPdfSemaphore,
            GuidebookPdfProperties properties,
            GuidebookPdfMetrics metrics,
            Clock clock) {
        this.restClient = guidebookPdfRestClient;
        this.concurrencyLimit = guidebookPdfSemaphore;
        this.maxResultBytes = properties.maxResultSize().toBytes();
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public byte[] convert(Long guidebookId, String contentHtml) {
        Objects.requireNonNull(guidebookId, "가이드북 ID는 null일 수 없습니다.");
        if (contentHtml == null || contentHtml.isBlank()) {
            throw new BusinessException(GuidebookErrorCode.GUIDEBOOK_NOT_READY);
        }
        if (!concurrencyLimit.tryAcquire()) {
            metrics.record("busy", Duration.ZERO, null);
            throw new BusinessException(GuidebookErrorCode.PDF_EXPORT_BUSY);
        }

        Instant startedAt = clock.instant();
        try {
            byte[] pdf = requestConversion(normalizeExternalResources(contentHtml));
            validatePdf(pdf);
            metrics.record("success", elapsedSince(startedAt), pdf.length);
            return pdf;
        } catch (BusinessException exception) {
            metrics.record(resultOf(exception), elapsedSince(startedAt), null);
            throw exception;
        } catch (RestClientException exception) {
            GuidebookErrorCode errorCode = isTimeout(exception)
                    ? GuidebookErrorCode.PDF_EXPORT_TIMEOUT
                    : GuidebookErrorCode.PDF_EXPORT_FAILED;
            metrics.record(errorCode == GuidebookErrorCode.PDF_EXPORT_TIMEOUT
                    ? "timeout"
                    : "failure", elapsedSince(startedAt), null);
            LOGGER.warn(
                    "PDF 변환기 호출 실패. guidebookId={}, failureType={}",
                    guidebookId,
                    exception.getClass().getSimpleName());
            throw sanitized(errorCode, exception);
        } finally {
            concurrencyLimit.release();
        }
    }

    private byte[] requestConversion(String contentHtml) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("files", htmlResource(contentHtml));
        form.add("emulatedMediaType", "print");
        form.add("printBackground", "true");
        form.add("preferCssPageSize", "true");
        form.add("skipNetworkAlmostIdleEvent", "true");
        form.add("waitDelay", "1s");
        form.add("failOnResourceLoadingFailed", "false");

        return restClient.post()
                .uri(CONVERSION_PATH)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    if (!status.is2xxSuccessful()) {
                        throw new PdfConverterResponseException(status.value());
                    }
                    return readLimited(response.getBody());
                });
    }

    private ByteArrayResource htmlResource(String contentHtml) {
        return new ByteArrayResource(contentHtml.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "index.html";
            }
        };
    }

    private String normalizeExternalResources(String contentHtml) {
        return contentHtml.replace(
                "http://tong.visitkorea.or.kr",
                "https://tong.visitkorea.or.kr");
    }

    private byte[] readLimited(InputStream inputStream) throws IOException {
        try (InputStream input = inputStream;
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxResultBytes) {
                    throw new BusinessException(GuidebookErrorCode.PDF_EXPORT_TOO_LARGE);
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private void validatePdf(byte[] pdf) {
        if (pdf.length < PDF_SIGNATURE.length()) {
            throw new BusinessException(GuidebookErrorCode.PDF_EXPORT_FAILED);
        }
        String signature = new String(
                pdf, 0, PDF_SIGNATURE.length(), StandardCharsets.US_ASCII);
        if (!PDF_SIGNATURE.equals(signature)) {
            throw new BusinessException(GuidebookErrorCode.PDF_EXPORT_FAILED);
        }
    }

    private Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, clock.instant());
    }

    private String resultOf(BusinessException exception) {
        if (exception.errorCode() == GuidebookErrorCode.PDF_EXPORT_BUSY) {
            return "busy";
        }
        if (exception.errorCode() == GuidebookErrorCode.PDF_EXPORT_TIMEOUT) {
            return "timeout";
        }
        if (exception.errorCode() == GuidebookErrorCode.PDF_EXPORT_TOO_LARGE) {
            return "too_large";
        }
        return "failure";
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != current) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private BusinessException sanitized(
            GuidebookErrorCode errorCode,
            RestClientException exception) {
        RuntimeException sanitizedCause = new RuntimeException(
                "PDF 변환기 요청 실패: " + exception.getClass().getSimpleName());
        return new BusinessException(errorCode, sanitizedCause);
    }

    private static final class PdfConverterResponseException extends RestClientException {

        private static final long serialVersionUID = 1L;

        private PdfConverterResponseException(int status) {
            super("PDF 변환기가 오류 상태를 반환했습니다: " + status);
        }
    }
}
