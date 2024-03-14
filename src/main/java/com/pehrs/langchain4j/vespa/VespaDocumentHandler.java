package com.pehrs.langchain4j.vespa;

import com.fasterxml.jackson.databind.JsonNode;
import com.pehrs.langchain4j.vespa.SimpleVespaEmbeddingStore.VespaDoc;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import java.util.Map;

public interface VespaDocumentHandler {

  String namespace();

  String docType();

  /**
   * Create a Vespa doc from a embedding and textSegment
   */
  VespaDoc createVespaDoc(String docId, Embedding embedding, TextSegment textSegment);

  /**
   * Extract the l4j metadata from the response from Vespa (fields)
   * @param jsonFields
   * @return
   */
  Map<String, String> getMetadata(JsonNode jsonFields);

  String getConent(JsonNode jsonFields);

  List<Float> getEmbedding(JsonNode jsonFields);

  String createYqlRequest(List<Float> queryEmbedding, int maxResults, double minScore);
}
