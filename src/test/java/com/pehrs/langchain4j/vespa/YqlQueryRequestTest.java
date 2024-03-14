package com.pehrs.langchain4j.vespa;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class YqlQueryRequestTest {

  @Test
  public void testYql() throws JsonProcessingException {
    List<Float> embedding = new ArrayList<Float>();
    for(var i =0;i<384;i++) {
      embedding.add(42.0f);
    }
    String queryEmbeddingStr = "[" + embedding.stream().map(d -> "" + d)
        .collect(Collectors.joining(",")) + "]";

    String yql = "select title, segment_index, embedding from books where ([{targetHits:5}]nearestNeighbor(embedding, q))";
    String rankingProfile = "recommendation";
    Map<String, Object> input = Map.of(
        "query(threshold)", 0.88f,
        "query(q)", embedding
    );
    YqlQueryRequest yqlRequest = new YqlQueryRequest(yql, input, rankingProfile);
    System.out.println(yqlRequest.toJson());
  }

}