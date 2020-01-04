package com.codeborne.selenide.impl;

import com.browserup.bup.util.HttpMessageContents;
import com.codeborne.selenide.proxy.FileDownloadFilter;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DownloaderTest {
  private final Downloader downloader = new Downloader();

  @Test
  void fileName() {
    assertThat(downloader.fileName("https://blah.com/foo.pdf?bar=zoo", Optional.of("mega.csv"))).isEqualTo("mega.csv");
    assertThat(downloader.fileName("https://blah.com/foo.pdf?bar=zoo", Optional.empty())).isEqualTo("foo.pdf");
  }

  @Test
  void saveFile() throws IOException {
    HttpMessageContents contents = mock(HttpMessageContents.class);
    when(contents.getBinaryContents()).thenReturn("CONTENT".getBytes(UTF_8));
    FileDownloadFilter.Response response = new FileDownloadFilter.Response("https://goo/boo.pdf", 200, "", emptyMap(), contents);

    File file = downloader.saveFile(new StaticConfig(), response);

    assertThat(file.getName()).isEqualTo("boo.pdf");
    assertThat(FileUtils.readFileToString(file, UTF_8)).isEqualTo("CONTENT");
  }
}
