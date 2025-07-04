package com.pehrs.langchain4j;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.pehrs.langchain4j.epub.EpubDocumentsReader;
import com.pehrs.langchain4j.metrics.ConsoleTableReporter;
import com.pehrs.langchain4j.rss.RssFeedReader;
import com.pehrs.langchain4j.util.CustomBatchIterator;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

      EmbeddingModel embeddingModel = RagSample.createEmbeddingModel(config);
      EmbeddingStore embeddingStore = RagSample.createEmbeddingStore(metricRegistry, config);
      DocumentsReader documentsReader = RagSample.createDocumentsReader(metricRegistry, config);

      createEmbeddings(metricRegistry, config, embeddingStore, embeddingModel, documentsReader);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  static void createEmbeddings(
      MetricRegistry metricRegistry,
      Config config,
      EmbeddingStore<TextSegment> embeddingStore,
      EmbeddingModel embeddingModel,
      DocumentsReader documentsReader) throws Exception {

    DocumentSplitter splitter =
        DocumentSplitters.recursive(
            config.getInt("embeddings.segments.maxSegmentSizeInTokens"),
            config.getInt("embeddings.segments.maxOverlapSizeInTokens"));

    Histogram generateHistogram = metricRegistry.histogram(VECTOR_GEN_MS);
    Histogram saveHistogram = metricRegistry.histogram(VECTOR_SAVE_MS);

    Stream<TextSegment> textSegments = documentsReader.readDocuments()
        .flatMap(document -> {
          if (document != null) {
            return splitter.split(document).stream();
          }
          log.warn("null document from document reader");
          return Stream.of();
        });

    int batchSize = config.getInt("embeddings.batchSize");
    CustomBatchIterator.batchStreamOf(textSegments, batchSize)
        .forEach(segments -> {
          // Generate Embeddings

          List<Embedding> embeddings = segments.stream().map(segment -> {
                long start = System.nanoTime();
                Embedding embedding = embeddingModel.embed(segment).content();
                generateHistogram.update(System.nanoTime() - start);
                return embedding;
              }).collect(Collectors.toList());

          // Save batch
          long start = System.nanoTime();
          embeddingStore.addAll(embeddings, segments);
          saveHistogram.update(System.nanoTime() - start);
        });
  }
}
