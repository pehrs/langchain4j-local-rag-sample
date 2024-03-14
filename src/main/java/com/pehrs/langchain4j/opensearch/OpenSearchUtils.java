package com.pehrs.langchain4j.opensearch;

import com.typesafe.config.Config;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.opensearch.OpenSearchEmbeddingStore;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.function.Factory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

public class OpenSearchUtils {

  public static EmbeddingStore<TextSegment> createOpenSearchEmbeddingStore(Config config) {

    Config opensearchConfig = config.getConfig("opensearch");
    try {
      URL opensearchUrl = new URL(opensearchConfig.getString("url"));
      String opensearchUsername = opensearchConfig.getString("username");
      String passwordEnvVar = opensearchConfig.getString("passwordEnvVar");
      String opensearchPassword = System.getenv(passwordEnvVar);
      String opensearchIndexName = opensearchConfig.getString("index");
      EmbeddingStore embeddingStore =
          new OpenSearchEmbeddingStore(
              createOpenSearchClient(opensearchUrl, opensearchUsername, opensearchPassword),
              opensearchIndexName
          );

      return embeddingStore;

    } catch (MalformedURLException | NoSuchAlgorithmException | KeyStoreException |
             KeyManagementException e) {
      throw new RuntimeException(e);
    }
  }


  static OpenSearchClient createOpenSearchClient(
      URL openSearchURl,
      String username,
      String password
  )
      throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

    String protocol = openSearchURl.getProtocol();
    String hostname = openSearchURl.getHost();
    int port = openSearchURl.getPort();

    final HttpHost host = new HttpHost(protocol, hostname, port);
    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    // Only for demo purposes. Don't specify your credentials in code.
    credentialsProvider.setCredentials(new AuthScope(host),
        new UsernamePasswordCredentials(username, password.toCharArray()));

    final SSLContext sslcontext = SSLContextBuilder
        .create()
        .loadTrustMaterial(null, (chains, authType) -> true)
        .build();

    final ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder.builder(
        host);
    builder.setHttpClientConfigCallback(httpClientBuilder -> {
      final TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
          .setSslContext(sslcontext)
          // See https://issues.apache.org/jira/browse/HTTPCLIENT-2219
          .setTlsDetailsFactory(new Factory<SSLEngine, TlsDetails>() {
            @Override
            public TlsDetails create(final SSLEngine sslEngine) {
              return new TlsDetails(sslEngine.getSession(), sslEngine.getApplicationProtocol());
            }
          })
          .build();

      final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder
          .create()
          .setTlsStrategy(tlsStrategy)
          .build();

      return httpClientBuilder
          .setDefaultCredentialsProvider(credentialsProvider)
          .setConnectionManager(connectionManager);
    });

    final OpenSearchTransport transport = builder.build();
    return new OpenSearchClient(transport);
  }

}
