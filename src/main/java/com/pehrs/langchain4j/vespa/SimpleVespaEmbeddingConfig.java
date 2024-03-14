package com.pehrs.langchain4j.vespa;

import com.typesafe.config.Config;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;

public class SimpleVespaEmbeddingConfig {

  public final String url;
  public final Duration timeout;

  public final String rankProfile;
  public final String rankingInputName;
  public final boolean avoidDups;

  public final String vespaDocumentHandler;

  SimpleVespaEmbeddingConfig(String url, Duration timeout,
      String rankProfile, String rankingInputName, boolean avoidDups, String vespaDocumentHandler) {
    this.url = url;
    this.timeout = timeout;
    this.rankProfile = rankProfile;
    this.rankingInputName = rankingInputName;
    this.avoidDups = avoidDups;
    this.vespaDocumentHandler = vespaDocumentHandler;

  }

  public VespaDocumentHandler createVespaDocumentHandler() {
    try {
      return (VespaDocumentHandler) Class.forName(this.vespaDocumentHandler).getConstructors()[0].newInstance();
    } catch (ClassNotFoundException | InvocationTargetException | InstantiationException |
             IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static VespaEmbeddingConfigBuilder builder() {
    return new VespaEmbeddingConfigBuilder();
  }

  public static VespaEmbeddingConfigBuilder fromConfig(Config vespaConfig) {

    VespaEmbeddingConfigBuilder builder = builder();

    if (vespaConfig.hasPath("url")) {
      builder.setUrl(vespaConfig.getString("url"));
    }
    if (vespaConfig.hasPath("ns")) {
      builder.setNamespace(vespaConfig.getString("ns"));
    }
    if (vespaConfig.hasPath("docType")) {
      builder.setDocumentType(vespaConfig.getString("docType"));
    }
    if (vespaConfig.hasPath("rankProfile")) {
      builder.setRankProfile(vespaConfig.getString("rankProfile"));
    }
    if (vespaConfig.hasPath("rankingInputName")) {
      builder.setRankingInputName(vespaConfig.getString("rankingInputName"));
    }
    if (vespaConfig.hasPath("timeout")) {
      builder.setTimeout(vespaConfig.getDuration("timeout"));
    }
    if (vespaConfig.hasPath("avoidDups")) {
      builder.setAvoidDups(vespaConfig.getBoolean("avoidDups"));
    }
    if (vespaConfig.hasPath("vespaDocumentHandler")) {
      builder.setVespaDocumentHandler(vespaConfig.getString("vespaDocumentHandler"));
    }

    return builder;
  }

  public static class VespaEmbeddingConfigBuilder {

    private String url;
    private Duration timeout;
    private String namespace;
    private String documentType;
    private String rankProfile;
    private String rankingInputName;
    private boolean avoidDups;

    private String vespaDocumentHandler;


    public VespaEmbeddingConfigBuilder() {
      this.url = "http://localhost:8080";
      this.timeout = Duration.ofSeconds(5);
      this.namespace = "embeddings";
      this.documentType = "embeddings";
      this.rankProfile = "recommendation";
      this.rankingInputName = "q_embedding";
      this.avoidDups = true;
      this.vespaDocumentHandler = null;

    }

    public VespaEmbeddingConfigBuilder setUrl(String url) {
      this.url = url.endsWith("/")?url.substring(0, url.length() -1):url;
      return this;
    }

    public VespaEmbeddingConfigBuilder setTimeout(Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    public VespaEmbeddingConfigBuilder setNamespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    public VespaEmbeddingConfigBuilder setDocumentType(String documentType) {
      this.documentType = documentType;
      return this;
    }

    public VespaEmbeddingConfigBuilder setRankProfile(String rankProfile) {
      this.rankProfile = rankProfile;
      return this;
    }

    public VespaEmbeddingConfigBuilder setRankingInputName(String rankingInputName) {
      this.rankingInputName = rankingInputName;
      return this;
    }

    public VespaEmbeddingConfigBuilder setAvoidDups(boolean avoidDups) {
      this.avoidDups = avoidDups;
      return this;
    }

    public VespaEmbeddingConfigBuilder setVespaDocumentHandler(String handlerClassName) {
      this.vespaDocumentHandler = handlerClassName;
      return this;
    }


    public SimpleVespaEmbeddingConfig build() {
      return new SimpleVespaEmbeddingConfig(
          url, timeout, rankProfile, rankingInputName, avoidDups, vespaDocumentHandler
      );
    }

  }
}
