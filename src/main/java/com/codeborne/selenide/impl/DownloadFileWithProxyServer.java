package com.codeborne.selenide.impl;

import com.codeborne.selenide.Config;
import com.codeborne.selenide.proxy.FileDownloadFilter;
import com.codeborne.selenide.proxy.SelenideProxyServer;
import com.google.common.base.Predicate;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DownloadFileWithProxyServer {
  private static final Logger log = LoggerFactory.getLogger(DownloadFileWithProxyServer.class);

  private final Waiter waiter;
  private final Downloader downloader;

  DownloadFileWithProxyServer(Waiter waiter, Downloader downloader) {
    this.waiter = waiter;
    this.downloader = downloader;
  }

  public DownloadFileWithProxyServer() {
    this(new Waiter(), new Downloader());
  }

  public File download(WebElementSource anyClickableElement,
                       WebElement clickable, SelenideProxyServer proxyServer, long timeout) throws FileNotFoundException {
    return clickAndInterceptFileByProxyServer(anyClickableElement, clickable, proxyServer, timeout);
  }

  private File clickAndInterceptFileByProxyServer(WebElementSource anyClickableElement, WebElement clickable,
                                          SelenideProxyServer proxyServer, long timeout) throws FileNotFoundException {
    Config config = anyClickableElement.driver().config();
    WebDriver webDriver = anyClickableElement.driver().getWebDriver();
    String currentWindowHandle = webDriver.getWindowHandle();
    Set<String> currentWindows = webDriver.getWindowHandles();

    FileDownloadFilter filter = proxyServer.responseFilter("download");
    filter.activate();
    try {
      clickable.click();

      waiter.wait(filter, new HasDownloads(), timeout, config.pollingInterval());
      return firstDownloadedFile(anyClickableElement, filter, timeout);
    }
    finally {
      filter.deactivate();
      closeNewWindows(webDriver, currentWindowHandle, currentWindows);
    }
  }

  private void closeNewWindows(WebDriver webDriver, String currentWindowHandle, Set<String> currentWindows) {
    Set<String> windowHandles = webDriver.getWindowHandles();
    if (windowHandles.size() != currentWindows.size()) {
      Set<String> newWindows = new HashSet<>(windowHandles);
      newWindows.removeAll(currentWindows);

      log.info("File has been opened in a new window, let's close {} new windows", newWindows.size());

      for (String newWindow : newWindows) {
        log.info("  Let's close {}", newWindow);
        try {
          webDriver.switchTo().window(newWindow);
          webDriver.close();
        }
        catch (NoSuchWindowException windowHasBeenClosedMeanwhile) {
          log.info("  Failed to close {}: {}", newWindow, Cleanup.of.webdriverExceptionMessage(windowHasBeenClosedMeanwhile));
        }
        catch (Exception e) {
          log.warn("  Failed to close {}", newWindow, e);
        }
      }
      webDriver.switchTo().window(currentWindowHandle);
    }
  }

  private static class HasDownloads implements Predicate<FileDownloadFilter> {
    @Override
    public boolean apply(FileDownloadFilter filter) {
      return filter.hasPotentialDownloads();
    }
  }

  private File firstDownloadedFile(WebElementSource anyClickableElement,
                                   FileDownloadFilter filter, long timeout) throws FileNotFoundException {
    List<FileDownloadFilter.Response> downloads = filter.getPotentialDownloads();

    if (downloads.isEmpty()) {
      throw new FileNotFoundException("Failed to download file " + anyClickableElement +
        " in " + timeout + " ms." + filter.allResponsesAsString());
    }

    // TODO sort by "likeness"
    Config config = anyClickableElement.driver().config();
    File file = downloader.saveFile(config, downloads.get(0));
    log.info("Downloaded file: {}", file.getAbsolutePath());
    log.info("Just in case, all intercepted responses: {}", filter.allResponsesAsString());
    return file;
  }
}
