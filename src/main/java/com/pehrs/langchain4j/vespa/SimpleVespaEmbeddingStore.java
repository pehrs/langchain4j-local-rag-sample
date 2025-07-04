package com.pehrs.langchain4j.vespa;

import static dev.langchain4j.internal.Utils.generateUUIDFrom;
import static dev.langchain4j.internal.Utils.randomUUID;

import ai.vespa.feed.client.DocumentId;
import ai.vespa.feed.client.FeedClientBuilder;
import ai.vespa.feed.client.FeedException;
import ai.vespa.feed.client.JsonFeeder;
import ai.vespa.feed.client.Result;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pehrs.langchain4j.RagSample;
import com.typesafe.config.Config;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * endpoints of vespa via http (typically http://localhost:8080/) without certificates. This enables
 * you to run vespa locally as a simple docker container:
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
 *
 * @see dev.langchain4j.store.embedding.vespa.VespaEmbeddingStore
 */
public class SimpleVespaEmbeddingStore implements EmbeddingStore<TextSegment>, Closeable {

  static Logger log = LoggerFactory.getLogger(SimpleVespaEmbeddingStore.class);


  private static ObjectMapper objectMapper = new ObjectMapper();
  private final CloseableHttpAsyncClient httpClient;
  private final SimpleVespaEmbeddingConfig config;
  private final VespaDocumentHandler vespaDocumentHandler;

  public SimpleVespaEmbeddingStore(MetricRegistry metricRegistry,
      SimpleVespaEmbeddingConfig config) {
    // Remove any trailing slash
    this.config = config;

    int maxSize = 16 * 1024 * 1024;

    final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
        .setSoTimeout(Timeout.of(config.timeout))
        .setSndBufSize(maxSize)
        .setRcvBufSize(maxSize)
        .build();

    this.httpClient = HttpAsyncClients.custom()
        .setIOReactorConfig(ioReactorConfig)
        .build();
    this.httpClient.start();

    this.vespaDocumentHandler = this.config.createVespaDocumentHandler();

  }

  JsonFeeder buildJsonFeeder() {
    FeedClientBuilder feedClientBuilder = FeedClientBuilder
        .create(URI.create(config.feedUrl));

    if (config.enableTls) {
//        CertificateFactory cf = CertificateFactory.getInstance("X.509");
//        // String certStr = "";
//        //byte[] decoded = Base64.getDecoder().decode(certStr);
//        // try (InputStream targetStream = new ByteArrayInputStream(certStr.getBytes())) {
//        // FIXME: Do we need Base64 decoding here?
//        try (InputStream targetStream = new FileInputStream(config.getCaCertPath().toFile())) {
//          X509Certificate caCert = (X509Certificate) cf.generateCertificate(targetStream);
//          feedClientBuilder.setCaCertificates(List.of(
//              caCert
//          ));
//        } catch (IOException e) {
//          throw new RuntimeException(e);
//        }
      feedClientBuilder.setCaCertificatesFile(config.getCaCertPath());
      feedClientBuilder.setCertificate(config.getClientCertPath(), config.getClientKeyPath());

    }
    return JsonFeeder
        .builder(feedClientBuilder.build())
        .withTimeout(config.timeout)
        .build();
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
    // add(embedding, null);
    throw new RuntimeException("Not supported");
  }


  @Override
  public List<String> addAll(List<Embedding> embeddings) {
    // return addAll(embeddings, null);
    throw new RuntimeException("Not supported");
  }

  @Override
  public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
    if (textSegments != null && embeddings.size() != textSegments.size()) {
      throw new IllegalArgumentException(
          "The list of embeddings and embedded must have the same size");
    }

    List<String> ids = new ArrayList<>();

    try (JsonFeeder jsonFeeder = buildJsonFeeder()) {
      List<VespaInsertReq> records = new ArrayList<>();

      for (int i = 0; i < embeddings.size(); i++) {
        String docId = createDocId(textSegments.get(i));
        DocumentId documentId = this.vespaDocumentHandler.createDocumentId(docId);
        records.add(this.vespaDocumentHandler.createVespaInsertReq(documentId,
            embeddings.get(i), textSegments.get(i)));
      }

      if (!records.isEmpty()) {

        try (InputStream recordInput = new ByteArrayInputStream(objectMapper.writeValueAsString(records)
            .getBytes(StandardCharsets.UTF_8))) {

          jsonFeeder.feedMany(
              recordInput, // Json.toInputStream(records, List.class),
              new JsonFeeder.ResultCallback() {
                @Override
                public void onNextResult(Result result, FeedException error) {
                  if (error != null) {
                    throw new RuntimeException(error.getMessage());
                  } else if (Result.Type.success.equals(result.type())) {
                    ids.add(result.documentId().toString());
                  }
                }

                @Override
                public void onError(FeedException error) {
                  throw new RuntimeException(error.getMessage());
                }
              }
          );
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return ids;

  }


  private String createDocId(TextSegment textSegment) {
    String srcId = textSegment.metadata().getString(RagSample.METADATA_SRC_ID);
    String docId = srcId != null
        ? srcId
        : config.avoidDups && textSegment != null ? generateUUIDFrom(textSegment.text())
            : randomUUID();
    String segmentIndex = textSegment.metadata().getString(RagSample.METADATA_SEGMENT_INDEX);
    if (segmentIndex != null) {
      docId += "-" + segmentIndex;
    }
    return docId;
  }

  @Override
  public void close() throws IOException {
    httpClient.close(CloseMode.GRACEFUL);
  }


  public record VespaEmbedding(List<Float> values) {

  }

  public record VespaInsertReq(String id, Map<String, Object> fields) {

  }

  public record VespaDoc(Map<String, Object> fields) {

    public VespaInsertReq asVespaInsertReq(DocumentId docId) {
      return new VespaInsertReq(docId.toString(), this.fields);
    }
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
    return this.addAll(List.of(embedding), List.of(textSegment)).get(0);

//    String srcId = textSegment.metadata().get(RagSample.METADATA_SRC_ID);
//    String docId = srcId != null
//        ? srcId
//        : config.avoidDups && textSegment != null ? generateUUIDFrom(textSegment.text())
//            : randomUUID();
//    String segmentIndex = textSegment.metadata().get(RagSample.METADATA_SEGMENT_INDEX);
//    if(segmentIndex!=null) {
//      docId += "-" + segmentIndex;
//    }
//
//    DocumentId documentId =
//        DocumentId.of(this.vespaDocumentHandler.namespace(), this.vespaDocumentHandler.docType(), docId);
//
//
//    VespaDoc vespaDoc = this.vespaDocumentHandler.createVespaDoc(docId, embedding, textSegment);
//
//    try {
//      String vespaJson = objectMapper.writeValueAsString(vespaDoc);
//      String uploadUrl = vespaDocApiUrl(docId);
//
//      final SimpleHttpRequest request = SimpleRequestBuilder.post(uploadUrl)
//          .setBody(vespaJson, ContentType.APPLICATION_JSON)
//          .build();
//
//      log.trace("HTTP POST {}", request.getBodyText());
//
//      Future<SimpleHttpResponse> httpResFuture = httpClient.execute(
//          SimpleRequestProducer.create(request),
//          SimpleResponseConsumer.create(),
//          new FutureCallback<>() {
//            @Override
//            public void completed(final SimpleHttpResponse response) {
//              log.debug(request + "->" + new StatusLine(response));
//              log.trace("" + response.getBodyText());
//            }
//
//            @Override
//            public void failed(final Exception ex) {
//              System.out.println(request + "->" + ex);
//            }
//
//            @Override
//            public void cancelled() {
//              System.out.println(request + " cancelled");
//            }
//          });
//
//      // Wait for response (logged in above code)
//      httpResFuture.get(this.config.timeout.getSeconds(), TimeUnit.SECONDS);
//
//      return documentId.toString();
//    } catch (JsonProcessingException|ExecutionException |InterruptedException |TimeoutException e) {
//      throw new RuntimeException(e);
//    }
  }

  @Override
  public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest embeddingSearchRequest) {

    double minScore = embeddingSearchRequest.minScore();
    int maxResults = embeddingSearchRequest.maxResults();
    Embedding referenceEmbedding = embeddingSearchRequest.queryEmbedding();
    try {
      YqlQueryRequest yqlRequest = this.vespaDocumentHandler.createYqlQueryRequest(
          referenceEmbedding.vectorAsList(), maxResults,
          minScore);

      final SimpleHttpRequest httpRequest = SimpleRequestBuilder.post(this.config.url + "/search/")
          .setBody(yqlRequest.toJson(), ContentType.APPLICATION_JSON)
          .build();

      Future<SimpleHttpResponse> httpResFuture = httpClient.execute(
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

      return new EmbeddingSearchResult(matches);

    } catch (IOException | ExecutionException | InterruptedException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private EmbeddingMatch<TextSegment> toEmbeddingMatch(JsonNode childNode) {

    String docId = childNode.get("id").asText();
    double relevance = childNode.get("relevance").asDouble();
    JsonNode jsonFields = childNode.get("fields");

    String content = this.vespaDocumentHandler.getContent(jsonFields);
    List<Float> embedding = this.vespaDocumentHandler.getEmbedding(jsonFields);

    // Get the metadata
    Map<String, String> metadataMap = this.vespaDocumentHandler.getMetadata(jsonFields);

    return new EmbeddingMatch<>(
        relevance,
        docId,
        Embedding.from(embedding),
        TextSegment.from(content, new Metadata(metadataMap))
    );
  }


  public static EmbeddingStore<TextSegment> createSimpleVespaEmbeddingStore(
      MetricRegistry metricRegistry, Config config) {
    Config vespaConfig = config.getConfig("vespa");
    SimpleVespaEmbeddingConfig vespaEmbeddingConfig =
        SimpleVespaEmbeddingConfig.fromConfig(vespaConfig)
            .build();
    EmbeddingStore embeddingStore = new SimpleVespaEmbeddingStore(metricRegistry,
        vespaEmbeddingConfig);
    return embeddingStore;
  }


}
