package com.codeborne.selenide.proxy;

import com.browserup.bup.util.HttpMessageContents;
import com.browserup.bup.util.HttpMessageInfo;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class FileDownloadFilterTest implements WithAssertions {
  private FileDownloadFilter filter = new FileDownloadFilter();
  private HttpResponse response = mock(HttpResponse.class);
  private HttpMessageContents contents = mock(HttpMessageContents.class);
  private HttpMessageInfo messageInfo = mock(HttpMessageInfo.class);

  @BeforeEach
  void setUp() throws IOException {
    DefaultHttpHeaders headers = new DefaultHttpHeaders();
    headers.add("hkey-01", "hvalue-01");
    when(response.headers()).thenReturn(headers);

    when(contents.getContentType()).thenReturn("app/json");
    when(contents.getTextContents()).thenReturn("my-text");
    deleteDirectory(new File("build/downloads/random-text"));
  }

  private HttpHeaders mockHeaders() {
    HttpHeaders headers = new DefaultHttpHeaders();
    when(response.headers()).thenReturn(headers);
    return headers;
  }

  @Test
  void doesNothingIfNotActivated() {
    filter.deactivate();
    filter.filterResponse(response, contents, messageInfo);

    verifyNoMoreInteractions(response);
    verifyNoMoreInteractions(contents);
    verifyNoMoreInteractions(messageInfo);
  }

  @Test
  void doesNotInterceptResponsesWithCodeBelow200() {
    filter.activate();
    mockStatusCode(199, "below 200");
    filter.filterResponse(response, contents, messageInfo);

    assertThat(filter.allResponsesAsString())
      .isEqualTo("Intercepted 1 responses:\n  #1  null -> 199 \"below 200\" {hkey-01=hvalue-01} app/json  (7 bytes)\n");
  }

  private void mockStatusCode(int code, String reason) {
    when(response.status()).thenReturn(new HttpResponseStatus(code, reason));
  }

  @Test
  void doesNotInterceptResponsesWithCodeAbove300() {
    filter.activate();
    mockStatusCode(300, "300 or above");
    filter.filterResponse(response, contents, messageInfo);

    assertThat(filter.allResponsesAsString())
      .isEqualTo("Intercepted 1 responses:\n  #1  null -> 300 \"300 or above\" {hkey-01=hvalue-01} app/json  (7 bytes)\n");
  }

  @Test
  void doesNotInterceptResponsesWithoutDispositionHeader() {
    filter.activate();
    mockStatusCode(200, "200=success");
    mockHeaders();
    filter.filterResponse(response, contents, messageInfo);

    assertThat(filter.allResponsesAsString())
      .isEqualTo("Intercepted 1 responses:\n  #1  null -> 200 \"200=success\" {} app/json  (7 bytes)\n");
  }

  @Test
  void interceptsHttpResponse() {
    filter.activate();
    mockStatusCode(200, "200=success");
    mockHeaders().add("content-disposition", "attachement; filename=report.pdf");
    when(contents.getBinaryContents()).thenReturn(new byte[]{1, 2, 3, 4, 5});

    filter.filterResponse(response, contents, messageInfo);

    assertThat(filter.getResponses()).hasSize(1);
    assertThat(filter.getPotentialDownloads()).hasSize(1);
    assertThat(filter.getPotentialDownloads().get(0).contents).isSameAs(contents);
  }
}
