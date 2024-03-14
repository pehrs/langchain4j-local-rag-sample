package com.pehrs.langchain4j.epub;

import com.codahale.metrics.MetricRegistry;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import dev.langchain4j.data.document.Document;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EpubReaderTest {


  @Test
  public void testDir() {
    MetricRegistry metricRegistry = new MetricRegistry();
    Config config = ConfigFactory.parseMap(Map.of(
        "epub.dir", "books"
    ));

    EpubDocumentsReader reader = new EpubDocumentsReader(metricRegistry, config);
    Document doc = reader.read();
    if(doc!=null) {
      System.out.println(doc.text().length());
    }
  }

}