package com.albertoventurini.rosiesbooks.platform.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class UnexpectedErrorTest {

  private static final String PRIVATE_EXCEPTION_SENTINEL = "private-exception-sentinel";
  private static final String PRIVATE_QUERY_SENTINEL = "private-query-sentinel";

  private final List<LogRecord> records = new CopyOnWriteArrayList<>();
  private final Handler handler =
      new Handler() {
        @Override
        public void publish(LogRecord record) {
          records.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
      };

  @BeforeEach
  void captureUnexpectedErrorLogs() {
    Logger.getLogger(UnexpectedExceptionMapper.class.getName()).addHandler(handler);
  }

  @AfterEach
  void stopCapturingUnexpectedErrorLogs() {
    Logger.getLogger(UnexpectedExceptionMapper.class.getName()).removeHandler(handler);
  }

  @Test
  void returnsAndLogsOnlyPrivacySafeUnexpectedErrorDetails() {
    Response response =
        given()
            .queryParam("private", PRIVATE_QUERY_SENTINEL)
            .when()
            .get("/test-only/unexpected-error");

    response
        .then()
        .statusCode(500)
        .contentType("text/html; charset=UTF-8")
        .header("Cache-Control", "no-store")
        .header(
            "X-Correlation-ID",
            matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));

    String correlationId = response.header("X-Correlation-ID");
    String body = response.asString();
    assertThat(body, containsString("Something went wrong"));
    assertThat(body, containsString("We couldn&#39;t complete that request."));
    assertThat(body, containsString(correlationId));
    assertThat(body, not(containsString(PRIVATE_EXCEPTION_SENTINEL)));
    assertThat(body, not(containsString(PRIVATE_QUERY_SENTINEL)));
    assertThat(body, not(containsString("IllegalStateException")));
    assertThat(body, not(containsString("stack trace")));

    assertThat(records, hasSize(1));
    LogRecord record = records.getFirst();
    assertThat(
        record.getMessage(),
        is(
            "{\"event\":\"unexpected_server_error\",\"correlation_id\":\""
                + correlationId
                + "\",\"exception_class\":\"java.lang.IllegalStateException\"}"));
    assertThat(record.getThrown(), is((Throwable) null));
    assertThat(record.getMessage(), not(containsString(PRIVATE_EXCEPTION_SENTINEL)));
    assertThat(record.getMessage(), not(containsString(PRIVATE_QUERY_SENTINEL)));
    assertThat(record.getMessage(), not(containsString("at ")));
  }

  @Path("/test-only/unexpected-error")
  public static class ThrowingResource {

    @GET
    public String fail(@QueryParam("private") String ignoredPrivateInput) {
      throw new IllegalStateException(PRIVATE_EXCEPTION_SENTINEL);
    }
  }
}
