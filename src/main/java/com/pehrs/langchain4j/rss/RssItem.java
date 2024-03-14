package com.pehrs.langchain4j.rss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RssItem(// @JacksonXmlProperty
                      String title,
                      // @JacksonXmlProperty
                      String link,
                      // @JacksonXmlProperty
                      String guid) {

}
