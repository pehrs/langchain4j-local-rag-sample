package com.pehrs.langchain4j;

import static java.util.Arrays.asList;

import com.codahale.metrics.MetricRegistry;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import org.apache.commons.lang3.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RagSampleCli {

  static Logger log = LoggerFactory.getLogger(RagSampleCli.class);

  public static void main(String[] args) {
    Config config = ConfigFactory.load("rag-sample");

    Chat chat = createInteractiveChat(config);

    try (Scanner scanner = new Scanner(System.in)) {
      while (true) {
        System.out.println("==================================================");
        System.out.print("Question: ");
        String userQuery = scanner.nextLine();
        System.out.println("==================================================");

        if ("exit".equalsIgnoreCase(userQuery)) {
          break;
        }

        String answer = chat.answer(userQuery);
        // String answer = chat(config, userQuery);
        System.out.println("==================================================");
        System.out.println("Answer:\n" + WordUtils.wrap(answer, 120));
      }
    }

  }

  static Chat createInteractiveChat(Config config) {

    MetricRegistry metricRegistry = new MetricRegistry();
    ChatLanguageModel chatModel = RagSample.createChatLanguageModel(config);

    EmbeddingModel embeddingModel = RagSample.createEmbeddingModel();

    EmbeddingStore embeddingStore = RagSample.createEmbeddingStore(metricRegistry,config);

    ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(embeddingStore)
        .embeddingModel(embeddingModel)
        .minScore(config.getDouble("chat.minScore"))
        .maxResults(config.getInt("chat.maxResults"))
        .build();

    ContentInjector contentInjector = DefaultContentInjector.builder()
        .promptTemplate(RagSample.getPromptTemplate(config))
        .metadataKeysToInclude(asList("url", "title"))
        // .metadataKeysToInclude(asList("file_name", "index"))
        .build();

    RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
        .contentRetriever(contentRetriever)
        .contentInjector(contentInjector)
        .build();

    return AiServices.builder(Chat.class)
        .chatLanguageModel(chatModel)
        .retrievalAugmentor(retrievalAugmentor)
        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
        .build();
  }

  interface Chat {

    String answer(String query);
  }
}
