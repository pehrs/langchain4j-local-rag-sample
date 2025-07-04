package com.pehrs.langchain4j.vespa;

import ai.vespa.feed.client.DocumentId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.pehrs.langchain4j.RagSample;
import com.pehrs.langchain4j.epub.EpubDocumentsReader;
import com.pehrs.langchain4j.vespa.SimpleVespaEmbeddingStore.VespaDoc;
import com.pehrs.langchain4j.vespa.SimpleVespaEmbeddingStore.VespaEmbedding;
import com.pehrs.langchain4j.vespa.SimpleVespaEmbeddingStore.VespaInsertReq;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EpubVespaDocHandler implements VespaDocumentHandler {


  @Override
  public String namespace() {
    return "embeddings";
  }

  @Override
  public String docType() {
    return "books";
  }

  @Override
  public VespaInsertReq createVespaInsertReq(DocumentId docId, Embedding embedding, TextSegment textSegment) {
    Map<String, Object> fields = new HashMap<>();
    if (textSegment != null) {
      fields.put("content", textSegment.text());

      Metadata metadata = textSegment.metadata();
      if (metadata != null) {
        String index = metadata.getString(RagSample.METADATA_SEGMENT_INDEX);
        if (index != null) {
          fields.put("segment_index", Integer.parseInt(index));
        }
        String title = metadata.getString(EpubDocumentsReader.METADATA_TITLE);
        if (title != null) {
          fields.put("title", title);
        }
      }
    }
    fields.put("embedding", new VespaEmbedding(embedding.vectorAsList()));
    return new VespaInsertReq(docId.toString(), fields);
  }

  @Override
  public Map<String, String> getMetadata(JsonNode jsonFields) {
    Map<String, String> metadata = new HashMap<>();
    String title = jsonFields.get("title").asText();
    metadata.put(EpubDocumentsReader.METADATA_TITLE, title);
    int segmentIndex = jsonFields.get("segment_index").asInt();
    metadata.put(RagSample.METADATA_SEGMENT_INDEX, String.valueOf(segmentIndex));
    return metadata;
  }

  @Override
  public String getContent(JsonNode jsonFields) {
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
  public YqlQueryRequest createYqlQueryRequest(List<Float> queryEmbedding, int maxResults, double minScore) {

    String yql = String.format("select documentid, embedding, title, content, segment_index from books "
        + "where {targetHits:%d}nearestNeighbor(embedding,q_embedding)", maxResults);
    String rankingProfile = "recommendation";
    Map<String, Object> input = Map.of(
        "query(threshold)", minScore,
        "query(q_embedding)", queryEmbedding
    );
    YqlQueryRequest yqlRequest = new YqlQueryRequest(yql, input, rankingProfile);
    return yqlRequest;
  }
}
