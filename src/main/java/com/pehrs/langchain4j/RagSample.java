package com.pehrs.langchain4j;


import com.pehrs.langchain4j.opensearch.OpenSearchUtils;
import com.pehrs.langchain4j.vespa.SimpleVespaEmbeddingStore;
import com.typesafe.config.Config;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.time.Duration;
import org.jetbrains.annotations.NotNull;

public abstract class RagSample {

  /**
   * Copied from private member {@code HierarchicalDocumentSplitter.INDEX}.
   * For details see the HierarchicalDocumentSplitter source code.
   * @see dev.langchain4j.data.document.splitter.HierarchicalDocumentSplitter
   */
  public static final String METADATA_SEGMENT_INDEX = "index";

  /**
   * Doc id used to identify a source
   * Used together wit the METADATA_SEGMENT_INDEX to create a
   * unique id for text segments in the embedding stores.
   */
  public static final String METADATA_SRC_ID = "src-id";

  public static PromptTemplate getPromptTemplate(Config config) {
    return PromptTemplate.from(config.getString("prompt.template"));
  }

  @NotNull
  public static EmbeddingStore createEmbeddingStore(String embeddingStoreName, Config config) {
    switch (embeddingStoreName) {
      case "vespa":
        return SimpleVespaEmbeddingStore.createSimpleVespaEmbeddingStore(config);
      case "opensearch":
        return OpenSearchUtils.createOpenSearchEmbeddingStore(config);
      default:
        return new InMemoryEmbeddingStore();
    }
  }

  public static EmbeddingModel createEmbeddingModel() {
    // The AllMiniLmL6V2EmbeddingModel creates 384 floating vectors
    EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

    // Use GPU for embeddings :-) Might be slower but give larger vectors.
    // NOTE: Make sure to change your vector store schema to match the size of vector you are using.
    // EmbeddingModel embeddingModel =
    //    new OllamaEmbeddingModel(ollamaBaseUrl, ollamaModelName, ollamaTimeout, ollamaMaxRetries);

    return embeddingModel;
  }

  public static ChatLanguageModel createChatLanguageModel(Config config) {
    Config ollamaConfig = config.getConfig("ollama");
    String ollamaBaseUrl = ollamaConfig.getString("baseUrl");
    String ollamaModelName = ollamaConfig.getString("modelName");
    Duration ollamaTimeout = ollamaConfig.getDuration("timeout");
    Integer ollamaMaxRetries = ollamaConfig.getInt("maxRetries");

    ChatLanguageModel chatModel = OllamaChatModel.builder()
        .baseUrl(ollamaBaseUrl)
        .modelName(ollamaModelName)
        .timeout(ollamaTimeout)
        .maxRetries(ollamaMaxRetries)
        .build();
    return chatModel;
  }
}
