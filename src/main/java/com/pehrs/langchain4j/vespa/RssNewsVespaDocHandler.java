package com.pehrs.langchain4j.vespa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.pehrs.langchain4j.RagSample;
import com.pehrs.langchain4j.rss.RssFeedReader;
import com.pehrs.langchain4j.vespa.SimpleVespaEmbeddingStore.VespaDoc;
import com.pehrs.langchain4j.vespa.SimpleVespaEmbeddingStore.VespaEmbedding;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RssNewsVespaDocHandler implements VespaDocumentHandler {

  @Override
  public String namespace() {
    return "embeddings";
  }

  @Override
  public String docType() {
    return "news";
  }

  @Override
  public VespaDoc createVespaDoc(String docId, Embedding embedding, TextSegment textSegment) {
    Map<String, Object> fields = new HashMap<>();
    if (textSegment != null) {
      fields.put("content", textSegment.text());
      if (docId != null) {
        fields.put("news_id", docId);
      }
      Metadata metadata = textSegment.metadata();
      if (metadata != null) {
        String url = metadata.get(RssFeedReader.METADATA_URL);
        if (url != null) {
          fields.put("url", url);
        }
        String index = metadata.get(RagSample.METADATA_SEGMENT_INDEX);
        if (index != null) {
          fields.put("segment_index", Integer.parseInt(index));
        }
        String title = metadata.get(RssFeedReader.METADATA_TITLE);
        if (title != null) {
          fields.put("title", title);
        }
        String ts = metadata.get(RssFeedReader.METADATA_TS);
        if (ts != null) {
          fields.put("ts", ts);
        }
      }
    }
    fields.put("embedding", new VespaEmbedding(embedding.vectorAsList()));
    return new VespaDoc(fields);
  }

  @Override
  public Map<String, String> getMetadata(JsonNode jsonFields) {
    Map<String, String> metadata = new HashMap<>();
    String url = jsonFields.get("url").asText();
    metadata.put(Document.URL, url);
    int segmentIndex = jsonFields.get("segment_index").asInt();
    metadata.put(RagSample.METADATA_SEGMENT_INDEX, String.valueOf(segmentIndex));
    return metadata;
  }

  @Override
  public String getConent(JsonNode jsonFields) {
    return jsonFields.get("content").asText();
  }

  @Override
  public List<Float> getEmbedding(JsonNode jsonFields) {
    ArrayNode jsonEmbeddingValues =
        (ArrayNode) jsonFields.get("embedding").get("values");
    List<Float> embedding = new ArrayList<>();
    jsonEmbeddingValues.forEach(valueNode -> {
      Double embeddingValue = valueNode.asDouble();
      embedding.add(embeddingValue.floatValue());
    });
    return embedding;
  }

  @Override
  public String createYqlRequest(List<Float> queryEmbedding, int maxResults, double minScore) {
    String queryEmbeddingStr = queryEmbedding.stream().map(d -> "" + d)
        .collect(Collectors.joining(","));

//    String targetHits = String.format("{targetHits:%d}", maxResults);
//    String fields = "documentid, embedding, title, content, news_id, url, segment_index, ts";
//    String yqlRequest = this.queryTemplate.replace("{{targetHits}}", targetHits);
//    yqlRequest = yqlRequest.replace("{{rankingName}}", this.config.rankProfile);
//    yqlRequest = yqlRequest.replace("{{rankingInputName}}", this.config.rankingInputName);
//    yqlRequest = yqlRequest.replace("{{embeddingFieldName}}", this.config.embeddingField);
//    yqlRequest = yqlRequest.replace("{{docType}}", this.config.documentType);
//    yqlRequest = yqlRequest.replace("{{fields}}", fields);
//    yqlRequest = yqlRequest.replace("{{embedding}}", queryEmbeddingStr);
//    yqlRequest = yqlRequest.replace("{{threshold}}", String.valueOf(minScore));
//    yqlRequest = yqlRequest.replace("{{tsFieldName}}", this.config.tsField);

    return String.format("{\n"
        + "  \"yql\": \"select documentid, embedding, title, content, news_id, url, segment_index, ts from news where "
        + "{targetHits:%d}nearestNeighbor(embedding,q_embedding) order by ts desc\",\n"
        + "  \"input\": {\n"
        + "    \"input.query(threshold)\": %f,\n"
        + "    \"query(q_embedding)\": [\n"
        + "      %s\n"
        + "    ]\n"
        + "  },\n"
        + "  \"ranking\": \"recommendation\"\n"
        + "}", maxResults, minScore, queryEmbeddingStr);
  }
}
