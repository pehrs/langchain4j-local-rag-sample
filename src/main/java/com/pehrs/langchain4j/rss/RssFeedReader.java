package com.pehrs.langchain4j.rss;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.pehrs.langchain4j.DocumentsReader;
import com.pehrs.langchain4j.RagSample;
import com.typesafe.config.Config;
import dev.langchain4j.data.document.Document;
import jakarta.xml.bind.DatatypeConverter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class RssFeedReader implements Closeable, AutoCloseable, DocumentsReader {

  public static final String METADATA_URL = "url";
  public static final String METADATA_TITLE = "title";
  public static final String METADATA_NEWS_ID = "news_id";
  public static final String METADATA_TS = "ts";
  public static final String RSS_SOURCE_MS = "rss.source.ms";
  public static final String RSS_ARTICLE_MS = "rss.article.ms";

  static Logger log = LoggerFactory.getLogger(RssFeedReader.class);

  private final Stack<String> rssUrls;
  private final DocumentBuilder xmlBuilder;
  private final MetricRegistry metricRegistry;
  private CloseableHttpClient httpclient = HttpClients.createDefault();

  private AutoDetectParser autoDetectParser = new AutoDetectParser();

  private final Histogram rssSourceHistogram;
  private final Histogram rssArticleHistogram;

  private Stack<String> currentRssItemUrls = new Stack();

  public RssFeedReader(MetricRegistry metricRegistry, Config config)
      throws ParserConfigurationException {

    List<String> rssFeeds = config.getStringList("rss.feeds");
    // Allow for overrides
    String rssFeedsProp = System.getProperty("rss.feeds");
    if (rssFeedsProp != null) {
      rssFeeds = Arrays.stream(rssFeedsProp.split(",")).toList();
    }

    this.rssUrls = new Stack();
    this.rssUrls.addAll(rssFeeds);
    this.xmlBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

    this.metricRegistry = metricRegistry;

    this.rssSourceHistogram = metricRegistry.histogram(RSS_SOURCE_MS);
    this.rssArticleHistogram = metricRegistry.histogram(RSS_ARTICLE_MS);
    metricRegistry.register("rss.articles.pending",
        (Gauge<Integer>) () -> this.currentRssItemUrls.size());
    metricRegistry.register("rss.src.pending",
        (Gauge<Integer>) () -> this.rssUrls.size());
  }

  public synchronized Document read()
      throws Exception {
    String url = nextItemUrl();
    if (url != null) {
      try {
        log.trace("Reading RSS article: " + url);
        long start = System.nanoTime();
        Document document = extractDocument(url);
        this.rssArticleHistogram.update(System.nanoTime() - start);
        return document;
      } catch (RuntimeException ex) {
        // Let's skip to the next url...
        return read();
      }
    }
    return null;
  }

  static String getTs(Metadata metadata) {

    // article:published_time -> 2024-03-10T15:37:27Z
    // article:modified_time -> 2024-03-10T15:37:27Z
    DateTimeFormatter fmt = DateTimeFormatter.ISO_DATE_TIME;
    String timestamp = metadata.get("article:modified_time");
    if (timestamp == null) {
      timestamp = metadata.get("article:published_time");
    }
    if (timestamp == null) {
      // Wed Mar 13 2024 11:51:14 GMT+0000 (UTC)

      // fmt = DateTimeFormatter.RFC_1123_DATE_TIME;
      fmt = DateTimeFormatter.ofPattern("EEE MMM d u H:m:s O", Locale.ENGLISH);
      // article:modified -> Wed Mar 13 2024 11:51:14 GMT+0000 (UTC)
      // article:published -> ...
      timestamp = metadata.get("article:modified");
      if (timestamp == null) {
        timestamp = metadata.get("article:published");
      }
      if(timestamp != null) {
        timestamp = timestamp.replace(" (UTC)", "");
        if(timestamp.indexOf("GMT+0000") != -1) {
          timestamp = timestamp.replace("GMT+0000", "GMT+00:00");
        }
      }
    }

    try {
      long epoch = LocalDateTime.parse(timestamp, fmt)
          .atOffset(ZoneOffset.UTC).toInstant().toEpochMilli();
      return "" + epoch;
    } catch (DateTimeParseException ex) {
      ex.printStackTrace();
      return "0";
    }
  }

  static String getTitle(Metadata metadata) {
    String title = metadata.get("og:title");
    if (title != null) {
      return title;
    }
    title = metadata.get("twitter:title");
    if (title != null) {
      return title;
    }
    title = metadata.get("dc:title");
    if (title != null) {
      return title;
    }
    return null;
  }

  private Document extractDocument(String url)
      throws IOException, TikaException, SAXException {

    HttpGet get = new HttpGet(url);
    get.setHeader(HttpHeaders.USER_AGENT, USER_AGENT);
    try (CloseableHttpResponse res = this.httpclient.execute(get)) {
      final HttpEntity entity = res.getEntity();

      Metadata metadata = new Metadata(); // IGNORED For now
      ParseContext context = new ParseContext(); // IGNORED For now
      ContentHandler handler = new BodyContentHandler(-1);

      String newsId = DatatypeConverter.printHexBinary(url.getBytes(Charset.defaultCharset()));

      this.autoDetectParser.parse(entity.getContent(), handler, metadata, context);
      dev.langchain4j.data.document.Metadata l4jMetadata = dev.langchain4j.data.document.Metadata.from(
          Map.of(
              METADATA_URL, url,
              METADATA_TITLE, getTitle(metadata),
              METADATA_NEWS_ID, newsId,
              RagSample.METADATA_SRC_ID, newsId,
              METADATA_TS, getTs(metadata)
          ));
      return toDocument(handler.toString(), l4jMetadata);
    }
  }

  private Document toDocument(String txt, dev.langchain4j.data.document.Metadata metadata) {
    return Document.document(txt, metadata);
  }

  private String nextItemUrl() throws IOException, SAXException {
    if (currentRssItemUrls.empty()) {
      this.currentRssItemUrls = getNextListOfItems();
    }
    if (currentRssItemUrls.empty()) {
      return null;
    }
    return currentRssItemUrls.pop();
  }

  private static final XmlMapper xmlMapper = new XmlMapper();

  private static final String USER_AGENT = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:122.0) Gecko/20100101 Firefox/122.0";

  static {
    xmlMapper.disable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    xmlMapper.enable(SerializationFeature.INDENT_OUTPUT);
  }

  private Stack<String> getNextListOfItems() throws IOException, SAXException {
    if (rssUrls.empty()) {
      return new Stack();
    }
    String rssUrl = rssUrls.pop();
    log.info("Reading RSS source: " + rssUrl);

    long start = System.nanoTime();
    HttpGet get = new HttpGet(rssUrl);
    get.setHeader(HttpHeaders.USER_AGENT, USER_AGENT);
    try (CloseableHttpResponse res = this.httpclient.execute(get)) {
      final HttpEntity entity = res.getEntity();
      String responseBody = EntityUtils.toString(entity, "UTF-8");
      // Stats
      this.rssSourceHistogram.update(System.nanoTime() - start);

      if (responseBody == null) {
        return new Stack();
      }
      RssFeed rssFeed = xmlMapper.readValue(responseBody, RssFeed.class);
      Stack<String> itemUrls = new Stack<>();
      itemUrls.addAll(
          rssFeed.channel().items().stream()
              .map(rssItem -> rssItem.link() == null ? rssItem.guid() : rssItem.link())
              .collect(Collectors.toList())
      );

      final int size = itemUrls.size();
      metricRegistry.register("src:" + rssUrl, (Gauge<Integer>) () -> size);

      return itemUrls;
    }
  }

  @Override
  public void close() throws IOException {
    this.httpclient.close();
  }

  public Iterator<Document> iterator() {
    return new Iterator<Document>() {
      @Override
      public boolean hasNext() {
        return !(currentRssItemUrls.isEmpty() && rssUrls.isEmpty());
      }

      @Override
      public Document next() {
        try {
          return read();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  public Stream<Document> readDocuments() {
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED),
        false);
  }
}
