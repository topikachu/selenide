package com.codeborne.selenide.proxy;

import com.browserup.bup.filters.ResponseFilter;
import com.browserup.bup.util.HttpMessageContents;
import com.browserup.bup.util.HttpMessageInfo;
import com.codeborne.selenide.impl.HttpHelper;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileDownloadFilter implements ResponseFilter {
  private final HttpHelper httpHelper = new HttpHelper();
  private boolean active;
  private final List<Response> responses = new ArrayList<>();
  private final List<Response> potentialDownloads = new ArrayList<>();

  /**
   * Activate this filter.
   * Starting from this moment, it will record all responses that contain header "Content-Disposition".
   * These responses are supposed to contain a file being downloaded.
   */
  public void activate() {
    potentialDownloads.clear();
    responses.clear();
    active = true;
  }

  /**
   * Deactivate this filter.
   * Starting from this moment, it will not record any responses.
   */
  public void deactivate() {
    active = false;
  }

  public boolean isActive() {
    return active;
  }

  @Override
  public void filterResponse(HttpResponse response, HttpMessageContents contents, HttpMessageInfo messageInfo) {
    if (!isActive()) return;

    Response r = new Response(messageInfo.getUrl(),
      response.status().code(),
      response.status().reasonPhrase(),
      toMap(response.headers()),
      contents
    );
    responses.add(r);
    if (isLikelyDownload(r)) potentialDownloads.add(r);
  }

  private Map<String, String> toMap(HttpHeaders headers) {
    Map<String, String> map = new HashMap<>();
    for (Map.Entry<String, String> header : headers) {
      map.put(header.getKey(), header.getValue());
    }
    return map;
  }

  List<Response> getResponses() {
    return responses;
  }

  /**
   * @return list of all downloaded files since activation.
   */
  public List<Response> getPotentialDownloads() {
    return potentialDownloads;
  }

  public boolean hasPotentialDownloads() {
    return !potentialDownloads.isEmpty();
  }

  private boolean isLikelyDownload(Response response) {
    if (response.code < 200 || response.code >= 300) return false;
    if (httpHelper.getFileNameFromContentDisposition(response.headers).isPresent()) return true;
    return false;
  }

  /**
   * @return all intercepted http response (as a string) - it can be useful for debugging
   */
  public String allResponsesAsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Intercepted ").append(responses.size()).append(" responses:\n");

    int i = 0;
    for (Response response : responses) {
      sb.append("  #").append(++i).append("  ").append(response).append("\n");
    }
    return sb.toString();
  }

  public static class Response {
    public final String url;
    private final int code;
    private final String reasonPhrase;
    public final Map<String, String> headers;
    public final HttpMessageContents contents;

    public Response(String url, int code, String reasonPhrase, Map<String, String> headers,
                     HttpMessageContents contents) {
      this.url = url;
      this.code = code;
      this.reasonPhrase = reasonPhrase;
      this.headers = headers;
      this.contents = contents;
    }

    @Override
    public String toString() {
      return url + " -> " + code + " \"" + reasonPhrase + "\" " + headers + " " +
        contents.getContentType() + " " + " (" + contents.getTextContents().length() + " bytes)";
    }
  }
}
