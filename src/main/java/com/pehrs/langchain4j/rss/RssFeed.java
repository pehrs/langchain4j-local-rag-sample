package com.pehrs.langchain4j.rss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RssFeed(RssChannel channel) {

}