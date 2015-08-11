package de.icanmakeit.dev;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.joda.time.DateTime;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Hello world!
 */
@Slf4j
public class App {

    private static MetricRegistry metrics;
    private static ConsoleReporter metricsReporter;
    private static Counter bytesTransferred;
    private static Meter transferRate;

    public static void main(final String[] args) {
        final String urlString = JOptionPane.showInputDialog(null, "Enter URL to download", "Enter URL to download", JOptionPane.PLAIN_MESSAGE);
        if (StringUtils.isNotBlank(urlString)) {
            setupMetrics();

            try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
                final HttpGet getUrl = new HttpGet(urlString);
                final HttpResponse response = httpClient.execute(getUrl);
                log.info("main() - HTTP reponse status: {}", response.getStatusLine());
                for (final Header header : response.getAllHeaders()) {
                    log.info("main() - HEADER {}", header);
                }
                if (HttpStatus.SC_OK == response.getStatusLine().getStatusCode()) {
                    log.info("main() - response entity info:");
                    log.info("main()     - chunked  : {}", response.getEntity().isChunked());
                    log.info("main()     - streaming: {}", response.getEntity().isStreaming());
                    log.info("main()     - length   : {}", response.getEntity().getContentLength());
                    final HttpEntity entity = response.getEntity();
                    final InputStream inputStream = entity.getContent();
                    try (final OutputStream outputStream = new FileOutputStream(new File(getFilename(urlString)))) {
                        final byte[] buffer = new byte[65536];
                        int bytesRead;
                        bytesRead = inputStream.read(buffer);
                        while (0 <= bytesRead) {
                            bytesTransferred.inc(bytesRead);
                            transferRate.mark(bytesRead / 1024);
                            outputStream.write(buffer, 0, bytesRead);
                            bytesRead = inputStream.read(buffer);
                        }
                    }
                }
                log.info("main() - Finished downloading {}, read a total of {} bytes ({} KB/s)", getFilename(urlString), bytesTransferred.getCount(), transferRate.getMeanRate());
            }
            catch (final IOException e) {
                log.error("main() - IOException - {}", e.getMessage(), e);
            }
        }
    }

    protected static String getFilename(final String url) {
        return StringUtils.defaultString(StringUtils.substringBefore(StringUtils.substringAfterLast(url, "/"), "?"), DateTime.now().toString("yyyy-MM-dd_") + "_" + DigestUtils.sha1Hex(url));
    }

    private static void setupMetrics() {
        metrics = new MetricRegistry();
        metricsReporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.SECONDS)
                .formattedFor(Locale.GERMANY)
                .build();
        metricsReporter.start(5, TimeUnit.SECONDS);
        bytesTransferred = metrics.counter("Bytes transferred");
        transferRate = metrics.meter("Transfer rate");
    }
}
