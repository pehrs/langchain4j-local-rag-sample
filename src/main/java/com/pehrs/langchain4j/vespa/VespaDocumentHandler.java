package com.pehrs.langchain4j.vespa;

import ai.vespa.feed.client.DocumentId;
import com.fasterxml.jackson.databind.JsonNode;
import com.pehrs.langchain4j.vespa.SimpleVespaEmbeddingStore.VespaDoc;
import com.pehrs.langchain4j.vespa.SimpleVespaEmbeddingStore.VespaInsertReq;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import java.util.Map;

/**
 * The VespaDocumentHandler translates l4j Embeddings and TextSegments to Vespa fields and
 * creates Vespa Queries from user input embedding.
 *
 */
public interface VespaDocumentHandler {

  String namespace();

  String docType();

  default DocumentId createDocumentId(String docId) {
    DocumentId documentId =
        DocumentId.of(namespace(), docType(), docId);
    return documentId;
  }

  /**
   * Create a Vespa doc from a embedding and textSegment
   */
  VespaInsertReq createVespaInsertReq(DocumentId docId, Embedding embedding, TextSegment textSegment);

  /**
   * Extract the l4j metadata from the response from Vespa (fields)
   * @param jsonFields
   * @return
   */
  Map<String, String> getMetadata(JsonNode jsonFields);

  String getContent(JsonNode jsonFields);

  List<Float> getEmbedding(JsonNode jsonFields);

  YqlQueryRequest createYqlQueryRequest(List<Float> queryEmbedding, int maxResults, double minScore);
}
