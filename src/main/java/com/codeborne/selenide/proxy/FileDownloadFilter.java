package com.codeborne.selenide.proxy;

import com.browserup.bup.filters.ResponseFilter;
import com.browserup.bup.util.HttpMessageContents;
import com.browserup.bup.util.HttpMessageInfo;
import com.codeborne.selenide.Config;
import com.codeborne.selenide.impl.Downloader;
import com.codeborne.selenide.impl.HttpHelper;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class FileDownloadFilter implements ResponseFilter {
  private static final Logger log = LoggerFactory.getLogger(FileDownloadFilter.class);

  private final Config config;
  private final Downloader downloader;

  private HttpHelper httpHelper = new HttpHelper();
  private boolean active;
  private final List<File> downloadedFiles = new ArrayList<>();
  private final List<Response> responses = new ArrayList<>();

  public FileDownloadFilter(Config config) {
    this(config, new Downloader());
  }

  FileDownloadFilter(Config config, Downloader downloader) {
    this.config = config;
    this.downloader = downloader;
  }

  /**
   * Activate this filter.
   * Starting from this moment, it will record all responses that contain header "Content-Disposition".
   * These responses are supposed to contain a file being downloaded.
   */
  public void activate() {
    downloadedFiles.clear();
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

  @Override
  public void filterResponse(HttpResponse response, HttpMessageContents contents, HttpMessageInfo messageInfo) {
    if (!active) return;

    Response r = new Response(messageInfo.getUrl(),
      response.status().code(),
      response.status().reasonPhrase(),
      toMap(response.headers()),
      contents.getContentType(),
      contents.getTextContents()
    );
    responses.add(r);

    if (response.status().code() < 200 || response.status().code() >= 300) return;

    String fileName = getFileName(r);
    if (fileName == null) return;

    File file = downloader.prepareTargetFile(config, fileName);
    try {
      FileUtils.writeByteArrayToFile(file, contents.getBinaryContents());
      downloadedFiles.add(file);
    }
    catch (IOException e) {
      log.error("Failed to save downloaded file to {} for url {}", file.getAbsolutePath(), messageInfo.getUrl(), e);
    }
  }

  private Map<String, String> toMap(HttpHeaders headers) {
    Map<String, String> map = new HashMap<>();
    for (Map.Entry<String, String> header : headers) {
      map.put(header.getKey(), header.getValue());
    }
    return map;
  }

  /**
   * @return list of all downloaded files since activation.
   */
  public List<File> getDownloadedFiles() {
    return downloadedFiles;
  }

  String getFileName(Response response) {
    return httpHelper.getFileNameFromContentDisposition(response.headers)
      .orElseGet(() -> {
        log.info("Cannot extract file name from http headers. Found headers: ");
        for (Map.Entry<String, String> header : response.headers.entrySet()) {
          log.info("{}={}", header.getKey(), header.getValue());
        }

        String fileNameFromUrl = httpHelper.getFileName(response.url);
        return isNotBlank(fileNameFromUrl) ? fileNameFromUrl : downloader.randomFileName();
      });
  }

  /**
   * @return all intercepted http response (as a string) - it can be useful for debugging
   */
  public String getResponses() {
    StringBuilder sb = new StringBuilder();
    sb.append("Intercepted ").append(responses.size()).append(" responses:\n");

    int i = 0;
    for (Response response : responses) {
      sb.append("  #").append(++i).append("  ").append(response).append("\n");
    }
    return sb.toString();
  }

  private static class Response {
    private String url;
    private int code;
    private String reasonPhrase;
    private String contentType;
    private Map<String, String> headers;
    private String content;

    private Response(String url, int code, String reasonPhrase, Map<String, String> headers,
                     String contentType, String content) {
      this.url = url;
      this.code = code;
      this.reasonPhrase = reasonPhrase;
      this.headers = headers;
      this.contentType = contentType;
      this.content = content;
    }

    @Override
    public String toString() {
      return url + " -> " + code + " \"" + reasonPhrase + "\" " + headers + " " +
          contentType + " " + " (" + content.length() + " bytes)";
    }
  }
}
