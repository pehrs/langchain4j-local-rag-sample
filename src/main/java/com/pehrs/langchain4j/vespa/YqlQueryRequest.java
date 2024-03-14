package com.pehrs.langchain4j.vespa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public record YqlQueryRequest(String yql, Map<String, Object> input, String ranking) {
  static ObjectMapper mapper = new ObjectMapper();

  public String toJson() {
    try {
      return mapper.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
