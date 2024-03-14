package com.pehrs.langchain4j.vespa;

import static dev.langchain4j.internal.Utils.generateUUIDFrom;
import static dev.langchain4j.internal.Utils.randomUUID;

import ai.vespa.feed.client.DocumentId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.pehrs.langchain4j.RagSample;
import com.pehrs.langchain4j.rss.RssFeedReader;
import com.typesafe.config.Config;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This implementation is called "simple" as it assumes you only have access to the feed and query
 * endpoints of vespa via http (typically http://localhost:8080/) without certificates.
 * This enables you to run vespa locally as a simple docker container:
 * <pre>
 *   docker run --detach \
 *   --name vespa \
 *   --hostname vespa-tutorial \
 *   --publish 8080:8080 \
 *   --publish 19071:19071 \
 *   --publish 19092:19092 \
 *   --publish 19050:19050 \
 *   vespaengine/vespa:8
 * </pre>
 */
public class SimpleVespaEmbeddingStore implements EmbeddingStore<TextSegment>, Closeable {

  static Logger log = LoggerFactory.getLogger(SimpleVespaEmbeddingStore.class);



  private static ObjectMapper objectMapper = new ObjectMapper();
  private final CloseableHttpAsyncClient client;
  private final SimpleVespaEmbeddingConfig config;
//  private static final String queryTemplate =
//      new Scanner(SimpleVespaEmbeddingStore.class.getResourceAsStream("/vespa-query-template.json"),
//          "UTF-8")
//          .useDelimiter("\\A").next();
  private final VespaDocumentHandler vespaDocumentHandler;

  public SimpleVespaEmbeddingStore(SimpleVespaEmbeddingConfig config) {
    // Remove any trailing slash
    this.config = config;

    int maxSize = 16 * 1024 * 1024;

    final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
        .setSoTimeout(Timeout.of(config.timeout))
        .setSndBufSize(maxSize)
        .setRcvBufSize(maxSize)
        .build();

    this.client = HttpAsyncClients.custom()
        .setIOReactorConfig(ioReactorConfig)
        .build();
    this.client.start();

    this.vespaDocumentHandler = this.config.createVespaDocumentHandler();

  }

  @Override
  public String add(Embedding embedding) {
    return add(embedding, null);
  }

  /**
   * Adds a new embedding with provided ID to the store.
   *
   * @param id        "user-specified" part of document ID, find more details
   *                  <a href="https://docs.vespa.ai/en/documents.html#namespace">here</a>
   * @param embedding the embedding to add
   */
  @Override
  public void add(String id, Embedding embedding) {
    log.warn("add(String id, Embedding embedding) called. The id field is IGNORED in this implementation!!!");
    add(embedding, null);
  }



  @Override
  public List<String> addAll(List<Embedding> embeddings) {
    return addAll(embeddings, null);
  }

  @Override
  public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
    if (textSegments != null && embeddings.size() != textSegments.size()) {
      throw new IllegalArgumentException(
          "The list of embeddings and embedded must have the same size");
    }

    List<String> result = new ArrayList<>(embeddings.size());
    for (int i = 0; i < embeddings.size(); i++) {
      Embedding embedding = embeddings.get(i);
      TextSegment textSegment = textSegments == null ? null : textSegments.get(i);
      result.add(add(embedding, textSegment));
    }

    return result;
  }

  @Override
  public void close() throws IOException {
    client.close(CloseMode.GRACEFUL);
  }


  public record VespaEmbedding(List<Float> values) {

  }

  public record VespaDoc(Map<String, Object> fields) {

  }

  public record VespaInsertReq(String docId, String vespaJson) {

  }


  private String vespaDocApiUrl(String id) {
    return
        String.format("%s/document/v1/%s/%s/docid/%s",
            this.config.url,
            this.vespaDocumentHandler.namespace(), this.vespaDocumentHandler.docType(), id);
  }

  // private String add(String id, Embedding embedding, TextSegment textSegment) {

  @Override
  public String add(Embedding embedding, TextSegment textSegment) {

    String srcId = textSegment.metadata().get(RagSample.METADATA_SRC_ID);
    String docId = srcId != null
        ? srcId
        : config.avoidDups && textSegment != null ? generateUUIDFrom(textSegment.text())
            : randomUUID();
    String segmentIndex = textSegment.metadata().get(RagSample.METADATA_SEGMENT_INDEX);
    if(segmentIndex!=null) {
      docId += "-" + segmentIndex;
    }

    DocumentId documentId =
        DocumentId.of(this.vespaDocumentHandler.namespace(), this.vespaDocumentHandler.docType(), docId);


    VespaDoc vespaDoc = this.vespaDocumentHandler.createVespaDoc(docId, embedding, textSegment);

    try {
      String vespaJson = objectMapper.writeValueAsString(vespaDoc);
      String uploadUrl = vespaDocApiUrl(docId);

      final SimpleHttpRequest request = SimpleRequestBuilder.post(uploadUrl)
          .setBody(vespaJson, ContentType.APPLICATION_JSON)
          .build();

      log.trace("HTTP POST {}", request.getBodyText());

      Future<SimpleHttpResponse> httpResFuture = client.execute(
          SimpleRequestProducer.create(request),
          SimpleResponseConsumer.create(),
          new FutureCallback<>() {
            @Override
            public void completed(final SimpleHttpResponse response) {
              log.debug(request + "->" + new StatusLine(response));
              log.trace("" + response.getBodyText());
            }

            @Override
            public void failed(final Exception ex) {
              System.out.println(request + "->" + ex);
            }

            @Override
            public void cancelled() {
              System.out.println(request + " cancelled");
            }
          });

      // Wait for response (logged in above code)
      httpResFuture.get(this.config.timeout.getSeconds(), TimeUnit.SECONDS);

      return documentId.toString();
    } catch (JsonProcessingException|ExecutionException |InterruptedException |TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

//  private VespaDoc createVespaDoc(String docId, Embedding embedding,
//      TextSegment textSegment) {
//
//    Map<String, Object> fields = new HashMap<>();
//    if (textSegment != null) {
//      fields.put(config.contentField, textSegment.text());
//      if (docId != null) {
//        fields.put(config.newsIdField, docId);
//      }
//      Metadata metadata = textSegment.metadata();
//      if (metadata != null) {
//        String url = metadata.get(RssFeedReader.METADATA_URL);
//        if (url != null) {
//          fields.put(config.urlField, url);
//        }
//        String index = metadata.get(RagSample.METADATA_SEGMENT_INDEX);
//        if (index != null) {
//          fields.put(config.segmentIndexField, Integer.parseInt(index));
//        }
//        String title = metadata.get(RssFeedReader.METADATA_TITLE);
//        if (title != null) {
//          fields.put(config.titleField, title);
//        }
//        String ts = metadata.get(RssFeedReader.METADATA_TS);
//        if (ts != null) {
//          fields.put(config.tsField, ts);
//        }
//      }
//    }
//    fields.put(config.embeddingField, new VespaEmbedding(embedding.vectorAsList()));
//    return new VespaDoc(fields);
//  }

  @Override
  public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding,
      int maxResults, double minScore) {

    try {
      String yqlRequest =
          this.vespaDocumentHandler.createYqlRequest(referenceEmbedding.vectorAsList(), maxResults, minScore);

      final SimpleHttpRequest httpRequest = SimpleRequestBuilder.post(this.config.url + "/search/")
          .setBody(yqlRequest, ContentType.APPLICATION_JSON)
          .build();

      Future<SimpleHttpResponse> httpResFuture = client.execute(
          SimpleRequestProducer.create(httpRequest),
          SimpleResponseConsumer.create(),
          new FutureCallback<>() {
            @Override
            public void completed(final SimpleHttpResponse response) {
              log.debug(httpRequest + "->" + new StatusLine(response));
              log.trace("" + response.getBody());
            }

            @Override
            public void failed(final Exception ex) {
              System.err.println(httpRequest + "->" + ex);
            }

            @Override
            public void cancelled() {
              System.err.println(httpRequest + " cancelled");
            }
          });


      // Wait for response (logged in above code)
      SimpleHttpResponse httpResponse = httpResFuture.get(this.config.timeout.getSeconds(),
          TimeUnit.SECONDS);
      String responseBody = httpResponse.getBodyText();

      JsonNode responseJson = objectMapper.readTree(responseBody);
      JsonNode root = responseJson.get("root");
      if (root.has("errors")) {
        JsonNode errors = root.get("errors");
        throw new RuntimeException(objectMapper.writeValueAsString(errors));
      }
      JsonNode children = responseJson.get("root").get("children");
      List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
      children.forEach(childNode -> {
        EmbeddingMatch<TextSegment> embeddingMatch = toEmbeddingMatch(childNode);
        matches.add(embeddingMatch);
      });
      return matches;
    } catch (IOException | ExecutionException | InterruptedException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private EmbeddingMatch<TextSegment> toEmbeddingMatch(JsonNode childNode) {

    String docId = childNode.get("id").asText();
    double relevance = childNode.get("relevance").asDouble();
    JsonNode jsonFields = childNode.get("fields");
//    String content = jsonFields.get(this.config.contentField).asText();
//    ArrayNode jsonEmbeddingValues =
//        (ArrayNode) jsonFields.get(this.config.embeddingField).get("values");
//    List<Float> embedding = new ArrayList<>();
//    jsonEmbeddingValues.forEach(valueNode -> {
//      Double embeddingValue = valueNode.asDouble();
//      embedding.add(embeddingValue.floatValue());
//    });

    String content = this.vespaDocumentHandler.getConent(jsonFields);
    List<Float> embedding = this.vespaDocumentHandler.getEmbedding(jsonFields);

    // Get the metadata
    Map<String, String> metadataMap = this.vespaDocumentHandler.getMetadata(jsonFields);
//    metadataMap = new HashMap<>();
//    String url = jsonFields.get(this.config.urlField).asText();
//    metadataMap.put(Document.URL, url);
//    int segmentIndex = jsonFields.get(this.config.segmentIndexField).asInt();
//    metadataMap.put(RagSample.METADATA_SEGMENT_INDEX, String.valueOf(segmentIndex));

    return new EmbeddingMatch<>(
        relevance,
        docId,
        Embedding.from(embedding),
        TextSegment.from(content, new Metadata(metadataMap))
    );
  }

//  private String createYqlRequest(List<Float> queryEmbedding, int maxResults, double minScore) {
//    String queryEmbeddingStr = queryEmbedding.stream().map(d -> "" + d)
//        .collect(Collectors.joining(","));
//
//    String targetHits = String.format("{targetHits:%d}", maxResults);
//    String fields = String.format("documentid, %s, %s, %s, %s, %s, %s, %s",
//        this.config.embeddingField,
//        this.config.titleField,
//        this.config.contentField,
//        this.config.newsIdField,
//        this.config.urlField,
//        this.config.segmentIndexField,
//        this.config.tsField);
//
//    String yqlRequest = this.queryTemplate.replace("{{targetHits}}", targetHits);
//    yqlRequest = yqlRequest.replace("{{rankingName}}", this.config.rankProfile);
//    yqlRequest = yqlRequest.replace("{{rankingInputName}}", this.config.rankingInputName);
//    yqlRequest = yqlRequest.replace("{{embeddingFieldName}}", this.config.embeddingField);
//    yqlRequest = yqlRequest.replace("{{docType}}", this.config.documentType);
//    yqlRequest = yqlRequest.replace("{{fields}}", fields);
//    yqlRequest = yqlRequest.replace("{{embedding}}", queryEmbeddingStr);
//    yqlRequest = yqlRequest.replace("{{threshold}}", String.valueOf(minScore));
//    yqlRequest = yqlRequest.replace("{{tsFieldName}}", this.config.tsField);
//
//    return yqlRequest;
//  }



  public static EmbeddingStore<TextSegment> createSimpleVespaEmbeddingStore(Config config) {
    Config vespaConfig = config.getConfig("vespa");
    SimpleVespaEmbeddingConfig vespaEmbeddingConfig =
        SimpleVespaEmbeddingConfig.fromConfig(vespaConfig)
            .build();
    EmbeddingStore embeddingStore = new SimpleVespaEmbeddingStore(vespaEmbeddingConfig);
    return embeddingStore;
  }


}
