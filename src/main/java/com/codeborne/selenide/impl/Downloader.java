package com.codeborne.selenide.impl;

import com.codeborne.selenide.Config;
import com.codeborne.selenide.proxy.FileDownloadFilter;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.apache.commons.io.FileUtils.writeByteArrayToFile;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class Downloader {
  private final Randomizer random;
  private final HttpHelper httpHelper;

  Downloader() {
    this(new Randomizer(), new HttpHelper());
  }

  Downloader(Randomizer random, HttpHelper httpHelper) {
    this.random = random;
    this.httpHelper = httpHelper;
  }

  public String fileName(String url, Optional<String> fileNameFromHeader) {
    return fileNameFromHeader.orElseGet(() -> {
      String fileNameFromUrl = httpHelper.getFileName(url);
      return isNotBlank(fileNameFromUrl) ? fileNameFromUrl : random.text();
    });
  }

  File prepareTargetFile(Config config, String fileName) {
    File uniqueFolder = new File(config.reportsFolder(), random.text());
    if (uniqueFolder.exists()) {
      throw new IllegalStateException("Unbelievable! Unique folder already exists: " + uniqueFolder.getAbsolutePath());
    }
    if (!uniqueFolder.mkdirs()) {
      throw new RuntimeException("Failed to create folder " + uniqueFolder.getAbsolutePath());
    }

    return new File(uniqueFolder, fileName);
  }

  File saveFile(Config config, FileDownloadFilter.Response response) {
    Optional<String> fileNameFromHeader = httpHelper.getFileNameFromContentDisposition(response.headers);
    String fileName = fileName(response.url, fileNameFromHeader);

    File file = prepareTargetFile(config, fileName);
    try {
      writeByteArrayToFile(file, response.contents.getBinaryContents());
      return file;
    }
    catch (IOException e) {
      throw new RuntimeException(String.format("Failed to save downloaded file to %s for url %s", file.getAbsolutePath(), response.url), e);
    }
  }
}
