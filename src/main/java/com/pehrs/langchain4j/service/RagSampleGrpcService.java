package com.pehrs.langchain4j.service;

import com.codahale.metrics.MetricRegistry;
import com.pehrs.grpc.rag.sample.AskReply;
import com.pehrs.grpc.rag.sample.AskRequest;
import com.pehrs.grpc.rag.sample.RagSampleGrpc;
import com.pehrs.langchain4j.RagSample;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RagSampleGrpcService {
  private static final Logger logger = LoggerFactory.getLogger(RagSampleGrpc.class);

  private Server server;

  private void start() throws IOException {
    /* The port on which the server should run */
    int port = 4242;
    server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
        .addService(new AskImpl())
        .addService(ProtoReflectionService.newInstance())
        .build()
        .start();
    logger.info("Server started, listening on " + port);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        // Use stderr here since the logger may have been reset by its JVM shutdown hook.
        System.err.println("*** shutting down gRPC server since JVM is shutting down");
        try {
          RagSampleGrpcService.this.stop();
        } catch (InterruptedException e) {
          e.printStackTrace(System.err);
        }
        System.err.println("*** server shut down");
      }
    });
  }

  private void stop() throws InterruptedException {
    if (server != null) {
      server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  /**
   * Await termination on the main thread since the grpc library uses daemon threads.
   */
  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    final RagSampleGrpcService server = new RagSampleGrpcService();
    server.start();
    server.blockUntilShutdown();
  }

  // FIXME: Implement a streaming version of this that would push the answer as it is created.
  static class AskImpl extends RagSampleGrpc.RagSampleImplBase {

    Config config = ConfigFactory.load("rag-sample");
    MetricRegistry metricRegistry = new MetricRegistry();

    @Override
    public void ask(AskRequest req, StreamObserver<AskReply> responseObserver) {
      String answer = chat(metricRegistry, config, req.getQuestion());
      AskReply reply = AskReply.newBuilder().setAnswer(answer).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    static String chat(MetricRegistry metricRegistry, Config config, String input) {
      ChatModel chatModel = RagSample.createChatLanguageModel(config);
      EmbeddingModel embeddingModel = RagSample.createEmbeddingModel(config);
      EmbeddingStore embeddingStore = RagSample.createEmbeddingStore(metricRegistry, config);

      Embedding questionEmbedding = embeddingModel.embed(input).content();

      int maxResults = config.getInt("chat.maxResults");
      double minScore = config.getDouble("chat.minScore");
      EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
          .queryEmbedding(questionEmbedding)
          .maxResults(maxResults)
          .minScore(minScore)
          .build();
      EmbeddingSearchResult<TextSegment> embeddings = embeddingStore.search(req);
      List<EmbeddingMatch<TextSegment>> relevantEmbeddings = embeddings.matches();

      String contents = relevantEmbeddings.stream()
          .map(match -> match.embedded().text())
          .collect(Collectors.joining("\n\n"));

      Map<String, Object> variables = Map.of(
          "userMessage", input,
          "contents", contents,
          "nofArticles", 4
      );

      Prompt prompt = RagSample.getPromptTemplate(config).apply(variables);
      ChatResponse response = chatModel.chat(prompt.toUserMessage());
      String answer = response.aiMessage().text();

      return answer;
    }

  }
}
