package com.pehrs.langchain4j;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.pehrs.langchain4j.epub.EpubDocumentsReader;
import com.pehrs.langchain4j.metrics.ConsoleTableReporter;
import com.pehrs.langchain4j.rss.RssFeedReader;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.embedding.BertTokenizer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.xml.parsers.ParserConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RagSampleCreateEmbeddings {

  static Logger log = LoggerFactory.getLogger(RagSampleCreateEmbeddings.class);

  public static final String VECTOR_GEN_MS = "vector.gen.ms";
  public static final String VECTOR_SAVE_MS = "vector.save.ms";

  public static void main(String[] args) {
    MetricRegistry metricRegistry = new MetricRegistry();

    try (ConsoleTableReporter reporter = ConsoleTableReporter.forRegistry(metricRegistry)
        .withClock(Clock.defaultClock())
        .outputTo(System.out)
        .histogramScaleFactor(EpubDocumentsReader.PARSE_EPUB_MS, 1_000_000d)
        .histogramScaleFactor(VECTOR_GEN_MS, 1_000_000d)
        .histogramScaleFactor(VECTOR_SAVE_MS, 1_000_000d)
        .histogramScaleFactor(RssFeedReader.RSS_SOURCE_MS, 1_000_000d)
        .histogramScaleFactor(RssFeedReader.RSS_ARTICLE_MS, 1_000_000d)
        .build()){
      reporter.start(2, 2, TimeUnit.SECONDS);

      Config config = ConfigFactory.load("rag-sample");

      EmbeddingModel embeddingModel = RagSample.createEmbeddingModel();
      EmbeddingStore embeddingStore = RagSample.createEmbeddingStore(config.getString("embeddingStore"), config);
      DocumentsReader documentsReader = createDocumentsReader(metricRegistry, config);

      createEmbeddings(metricRegistry, embeddingStore, embeddingModel, documentsReader);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private static DocumentsReader createDocumentsReader(MetricRegistry metricRegistry, Config config) {
    try {
      String readerClassName = config.getString("documentsReader");
      Class<?> readerClass = Class.forName(readerClassName);
      Constructor<?> constructor = readerClass.getDeclaredConstructor(
          MetricRegistry.class, Config.class);
      return (DocumentsReader) constructor.newInstance(metricRegistry, config);
    } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
             InstantiationException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

//  @NotNull
//  private static RssFeedReader getRssFeedReader(MetricRegistry metricRegistry, Config config) throws ParserConfigurationException {
//    List<String> rssFeeds = config.getStringList("rss.feeds");
//    // Allow for overrides
//    String rssFeedsProp = System.getProperty("rss.feeds");
//    if (rssFeedsProp != null) {
//      rssFeeds = Arrays.stream(rssFeedsProp.split(",")).toList();
//    }
//    RssFeedReader rssFeedReader = new RssFeedReader(metricRegistry, rssFeeds);
//    return rssFeedReader;
//  }

  static void createEmbeddings(
      MetricRegistry metricRegistry,
      EmbeddingStore<TextSegment> embeddingStore,
      EmbeddingModel embeddingModel,
      DocumentsReader documentsReader) throws Exception {

    Tokenizer tokenizer = new BertTokenizer();
    DocumentSplitter splitter =
        DocumentSplitters.recursive(1000, 200, tokenizer);

    Histogram generateHistogram = metricRegistry.histogram(VECTOR_GEN_MS);
    Histogram saveHistogram = metricRegistry.histogram(VECTOR_SAVE_MS);

    documentsReader.readDocuments()
        .flatMap(document -> {
          if(document!=null) {
            return splitter.split(document).stream();
          }
          log.warn("null document from document reader");
          return Stream.of();
        })
        .forEach(segment -> {
          // Generate Embedding
          long start = System.nanoTime();
          Embedding embedding = embeddingModel.embed(segment).content();
          generateHistogram.update(System.nanoTime() - start);

          // Save
          String newsId = segment.metadata().get(RssFeedReader.METADATA_NEWS_ID);
          start = System.nanoTime();
          embeddingStore.add(embedding, segment);
          saveHistogram.update(System.nanoTime() - start);
        });
  }
}
