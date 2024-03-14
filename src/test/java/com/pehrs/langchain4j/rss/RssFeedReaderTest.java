package com.pehrs.langchain4j.rss;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.apache.tika.metadata.Metadata;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RssFeedReaderTest {

  @Test
  public void testArticleModified() {

    Metadata metadata = new Metadata();
    metadata.set("article:modified", "Wed Mar 13 2024 11:51:14 GMT+0000 (UTC)");
    String result = RssFeedReader.getTs(metadata);

    Assertions.assertEquals(1710330674000L, Long.parseLong(result));
  }

  @Test
  public void testArticleModifiedTime() {
    Metadata metadata = new Metadata();
    metadata.set("article:modified_time", "2024-03-10T15:37:27Z");
    String result = RssFeedReader.getTs(metadata);
    Assertions.assertEquals(1710085047000L, Long.parseLong(result));
  }

  @Test
  void testDt() {
    String timestamp = "2024-03-10T15:37:27Z";
    DateTimeFormatter fmt = DateTimeFormatter.ISO_DATE_TIME;

    long epoch = LocalDateTime.parse(timestamp, fmt)
        .atOffset(ZoneOffset.UTC).toInstant().toEpochMilli();
    System.out.println(epoch);
  }

}