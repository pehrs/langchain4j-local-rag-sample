package com.pehrs.langchain4j.vespa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.pehrs.langchain4j.RagSample;
import com.pehrs.langchain4j.epub.EpubDocumentsReader;
import com.pehrs.langchain4j.vespa.SimpleVespaEmbeddingStore.VespaDoc;
import com.pehrs.langchain4j.vespa.SimpleVespaEmbeddingStore.VespaEmbedding;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
  public VespaDoc createVespaDoc(String docId, Embedding embedding, TextSegment textSegment) {
    Map<String, Object> fields = new HashMap<>();
    if (textSegment != null) {
      fields.put("content", textSegment.text());

      Metadata metadata = textSegment.metadata();
      if (metadata != null) {
        String index = metadata.get(RagSample.METADATA_SEGMENT_INDEX);
        if (index != null) {
          fields.put("segment_index", Integer.parseInt(index));
        }
        String title = metadata.get(EpubDocumentsReader.METADATA_TITLE);
        if (title != null) {
          fields.put("title", title);
        }
      }
    }
    fields.put("embedding", new VespaEmbedding(embedding.vectorAsList()));
    return new VespaDoc(fields);
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

    return String.format("{\n"
        + "  \"yql\": \"select documentid, embedding, title, content, segment_index from books where "
        + "{targetHits:%d}nearestNeighbor(embedding,q_embedding)\",\n"
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
