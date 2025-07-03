package com.pehrs.langchain4j;

import static java.util.Arrays.asList;

import com.codahale.metrics.MetricRegistry;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.lang3.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RagSampleCli {

  static Logger log = LoggerFactory.getLogger(RagSampleCli.class);

  public static void main(String[] args) throws InterruptedException, ExecutionException {
    Config config = ConfigFactory.load("rag-sample");

    Chat ragChat = createRagChat(config);
    Chat regularChat = createRegularChat(config);

    try (Scanner scanner = new Scanner(System.in)) {
      while (true) {
        System.out.printf("%s==================================================%s%n", AnsiColors.BLACK_BRIGHT, AnsiColors.RESET);
        System.out.printf("%sQuestion%s: ", AnsiColors.YELLOW, AnsiColors.RESET);
        String userQuery = scanner.nextLine();
        if (userQuery == null || userQuery.length() == 0) {
          continue;
        }
        System.out.printf("%s==================================================%s%n", AnsiColors.BLACK_BRIGHT, AnsiColors.RESET);

        if ("exit".equalsIgnoreCase(userQuery)) {
          System.out.println("\nShutting down...");
          System.exit(0);
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);

        List<Future<String>> answers = pool.invokeAll(List.of(
            () -> ragChat.answer(userQuery),
            () -> regularChat.answer(userQuery)
        ));

        String ragAnswer = answers.get(0).get();
        String regularAnswer = answers.get(1).get();

        System.out.printf("--- %sRAG ANSWER%s ---%n",
            AnsiColors.GREEN, AnsiColors.RESET);
        System.out.println("Answer:\n" + WordUtils.wrap(ragAnswer, 120));

        System.out.printf("--- %s%s ANSWER%s ---%n",
            AnsiColors.GREEN, config.getString("ollama.modelName"), AnsiColors.RESET);
        System.out.println("Answer:\n" + WordUtils.wrap(regularAnswer, 120));
      }
    } catch (NoSuchElementException ex) {
      System.out.println("\nShutting down...");
      System.exit(0);
    }

  }

  static Chat createRagChat(Config config) {

    MetricRegistry metricRegistry = new MetricRegistry();
    ChatLanguageModel chatModel = RagSample.createChatLanguageModel(config);

    EmbeddingModel embeddingModel = RagSample.createEmbeddingModel();

    EmbeddingStore embeddingStore = RagSample.createEmbeddingStore(metricRegistry, config);

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


  static Chat createRegularChat(Config config) {

    ChatLanguageModel chatModel = RagSample.createChatLanguageModel(config);

    return AiServices.builder(Chat.class)
        .chatLanguageModel(chatModel)
        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
        .build();
  }


  interface Chat {

    String answer(String query);
  }
}
