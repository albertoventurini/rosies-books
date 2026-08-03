package com.albertoventurini.rosiesbooks.platform.web;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;
import org.jboss.logging.Logger;

@Provider
class UnexpectedExceptionMapper implements ExceptionMapper<Throwable> {

  static final String CORRELATION_HEADER = "X-Correlation-ID";
  private static final Logger LOG = Logger.getLogger(UnexpectedExceptionMapper.class);
  private static final MediaType HTML_UTF_8 = MediaType.valueOf("text/html; charset=UTF-8");

  @Override
  public Response toResponse(Throwable exception) {
    if (exception instanceof WebApplicationException webApplicationException) {
      return webApplicationException.getResponse();
    }

    String correlationId = UUID.randomUUID().toString();
    LOG.errorf(
        "{\"event\":\"unexpected_server_error\",\"correlation_id\":\"%s\",\"exception_class\":\"%s\"}",
        correlationId, exception.getClass().getName());

    ErrorPage page =
        new ErrorPage("Something went wrong", "We couldn't complete that request.", correlationId);
    return Response.serverError()
        .type(HTML_UTF_8)
        .header("Cache-Control", "no-store")
        .header(CORRELATION_HEADER, correlationId)
        .entity(WebTemplates.error(page).render())
        .build();
  }
}
