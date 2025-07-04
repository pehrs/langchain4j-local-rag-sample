package com.pehrs.langchain4j.vespa;

import com.typesafe.config.Config;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.Duration;

public class SimpleVespaEmbeddingConfig {

  public final String url;
  public final Duration timeout;

  public final String rankProfile;
  public final String rankingInputName;
  public final boolean avoidDups;

  public final int targetHits;

  public final String vespaDocumentHandler;

  public final String feedUrl;

  public final boolean enableTls;
  public final String caCertPath;
  public final String clientCertPath;
  public final String clientKeyPath;

  public final boolean logRequests;

  public SimpleVespaEmbeddingConfig(String url, Duration timeout, String rankProfile,
      String rankingInputName, boolean avoidDups,
      int targetHits,
      String vespaDocumentHandler,
      String feedUrl,
      boolean enableTls,
      boolean logRequests,
      String caCertPath, String clientCertPath, String clientKeyPath) {
    this.url = url;
    this.timeout = timeout;
    this.rankProfile = rankProfile;
    this.rankingInputName = rankingInputName;
    this.avoidDups = avoidDups;
    this.targetHits = targetHits;
    this.vespaDocumentHandler = vespaDocumentHandler;
    this.feedUrl = feedUrl;
    this.enableTls = enableTls;
    this.logRequests = logRequests;
    this.caCertPath = caCertPath;
    this.clientCertPath = clientCertPath;
    this.clientKeyPath = clientKeyPath;
  }

  public Path getCaCertPath() {
    return Path.of(this.caCertPath);
  }

  public Path getClientCertPath() {
    return Path.of(this.clientCertPath);
  }

  public Path getClientKeyPath() {
    return Path.of(this.clientKeyPath);
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
    if (vespaConfig.hasPath("targetHits")) {
      builder.setTargetHits(vespaConfig.getInt("targetHits"));
    }
    if (vespaConfig.hasPath("vespaDocumentHandler")) {
      builder.setVespaDocumentHandler(vespaConfig.getString("vespaDocumentHandler"));
    }

    if (vespaConfig.hasPath("vespaDocumentHandler")) {
      builder.setVespaDocumentHandler(vespaConfig.getString("vespaDocumentHandler"));
    }

    if (vespaConfig.hasPath("feedUrl")) {
      builder.setFeedUrl(vespaConfig.getString("feedUrl"));
    }
    if (vespaConfig.hasPath("logRequests")) {
      builder.setLogRequests(vespaConfig.getBoolean("logRequests"));
    }
    if (vespaConfig.hasPath("enableTls")) {
      builder.setEnableTls(vespaConfig.getBoolean("enableTls"));
    }
    if (vespaConfig.hasPath("caCertPath")) {
      builder.setCaCertPath(vespaConfig.getString("caCertPath"));
    }
    if (vespaConfig.hasPath("clientCertPath")) {
      builder.setClientCertPath(vespaConfig.getString("clientCertPath"));
    }
    if (vespaConfig.hasPath("clientKeyPath")) {
      builder.setClientKeyPath(vespaConfig.getString("clientKeyPath"));
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
    private int targetHits;
    private boolean logRequests;

    private String vespaDocumentHandler;


    public String feedUrl;
    public  boolean enableTls;
    public  String caCertPath;
    public  String clientCertPath;
    public  String clientKeyPath;

    public VespaEmbeddingConfigBuilder() {
      this.url = "http://localhost:8080";
      this.timeout = Duration.ofSeconds(5);
      this.namespace = "embeddings";
      this.documentType = "embeddings";
      this.rankProfile = "recommendation";
      this.rankingInputName = "q_embedding";
      this.avoidDups = true;
      this.targetHits = 5;
      this.vespaDocumentHandler = null;
      this.feedUrl = "https://localhost:9443";
      this.enableTls = false;
      this.logRequests = false;
      this.caCertPath = null;
      this.clientCertPath = null;
      this.clientKeyPath = null;
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

    public VespaEmbeddingConfigBuilder setTargetHits(int targetHits) {
      this.targetHits = targetHits;
      return this;
    }

    public VespaEmbeddingConfigBuilder setVespaDocumentHandler(String handlerClassName) {
      this.vespaDocumentHandler = handlerClassName;
      return this;
    }

    public VespaEmbeddingConfigBuilder setFeedUrl(String url) {
      this.feedUrl = url;
      return this;
    }

    public VespaEmbeddingConfigBuilder setLogRequests(boolean value) {
      this.logRequests = value;
      return this;
    }

    public VespaEmbeddingConfigBuilder setEnableTls(boolean value) {
      this.enableTls = value;
      return this;
    }

    public VespaEmbeddingConfigBuilder setCaCertPath(String path) {
      this.caCertPath = path;
      return this;
    }
    public VespaEmbeddingConfigBuilder setClientCertPath(String path) {
      this.clientCertPath = path;
      return this;
    }
    public VespaEmbeddingConfigBuilder setClientKeyPath(String path) {
      this.clientKeyPath = path;
      return this;
    }

    public SimpleVespaEmbeddingConfig build() {
      return new SimpleVespaEmbeddingConfig(
          url, timeout, rankProfile, rankingInputName, avoidDups, targetHits, vespaDocumentHandler,
          feedUrl,
          enableTls, logRequests, caCertPath, clientCertPath, clientKeyPath
      );
    }

  }
}
