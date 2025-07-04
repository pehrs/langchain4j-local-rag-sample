package com.pehrs.langchain4j.vespa;

import ai.vespa.client.dsl.A;
import ai.vespa.client.dsl.Annotation;
import ai.vespa.client.dsl.NearestNeighbor;
import ai.vespa.client.dsl.Q;
import ai.vespa.feed.client.FeedClientBuilder;
import ai.vespa.feed.client.FeedException;
import ai.vespa.feed.client.JsonFeeder;
import ai.vespa.feed.client.JsonFeeder.ResultCallback;
import ai.vespa.feed.client.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pehrs.langchain4j.vespa.SimpleVespaEmbeddingStore.VespaInsertReq;
import dev.langchain4j.internal.Json;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class SimpleVespaEmbeddingStoreTest {

  // @Test
  void feedTest() throws IOException {

    JsonFeeder feeder = JsonFeeder
        .builder(FeedClientBuilder.create(URI.create("http://localhost:8080")).build())
        .withTimeout(Duration.ofSeconds(5))
        .build();

    ArrayList<Float> embedding = new ArrayList<Float>();
    for(var i =0;i<384;i++) {
      embedding.add(42.0f);
    }

    String docId = "id:embeddings:books::4242-0";
    Map<String, Object> fields = Map.of(
        // "documentid", docId,
        "content", "content",
        "title", "title",
        "segment_index", 42,
        "embedding", embedding
    );
    ObjectMapper objectMapper = new ObjectMapper();
    VespaInsertReq insertReq = new VespaInsertReq(docId, fields);

    List<VespaInsertReq> records = new ArrayList<>();
    records.add(insertReq);

    String json = objectMapper.writeValueAsString(records);
    try (InputStream inStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
      CompletableFuture<Void> future = feeder.feedMany(
          inStream,
          new ResultCallback() {
            @Override
            public void onNextResult(Result result, FeedException error) {
              System.out.println("result: " + result);
              ResultCallback.super.onNextResult(result, error);
            }

            @Override
            public void onError(FeedException error) {
              throw new RuntimeException(error.getMessage());
            }
          }
      );
      future.join();
    }
  }


  @Test
  void testQ() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {

    ArrayList<Float> embedding = new ArrayList<Float>();
    for(var i =0;i<4;i++) {
      embedding.add(42.0f);
    }

    String searchQuery = Q
        .select("title", "segment_index", "embedding")
        .from("book")
        .where(buildNearestNeighbor())
        .fix()
        .hits(5)
        .ranking("recommendation")
        .param("input.query(q)", Json.toJson(embedding))
        .param("input.query(threshold)", String.valueOf(0.88))
        .build();

    System.out.println("" + searchQuery);
  }

  private NearestNeighbor buildNearestNeighbor()
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    NearestNeighbor nb = Q.nearestNeighbor("embedding", "q");

    // workaround to invoke ai.vespa.client.dsl.NearestNeighbor#annotate,
    // see https://github.com/vespa-engine/vespa/issues/28029
    // The bug is fixed in the meantime, but the baseline has been upgraded to Java 11, hence this workaround remains here
    Method method = NearestNeighbor.class.getDeclaredMethod("annotate", new Class<?>[] { Annotation.class });
    method.setAccessible(true);
    method.invoke(nb, A.a("targetHits", 5));
    return nb;
  }

}