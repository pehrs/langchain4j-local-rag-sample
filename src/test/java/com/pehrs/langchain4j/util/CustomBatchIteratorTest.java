package com.pehrs.langchain4j.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CustomBatchIteratorTest {

  @Test
  public void givenStreamOf11Ints_whenBatchStreamOf_thenProduce3Batches() {
    int batchSize = 4;
    Stream<Integer> data = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
        .stream();

    Collection<List<Integer>> result = new ArrayList<>();
    CustomBatchIterator.batchStreamOf(data, batchSize).forEach(result::add);
    assertTrue(result.contains(List.of(1, 2, 3, 4)));
    assertTrue(result.contains(List.of(5, 6, 7, 8)));
    assertTrue(result.contains(List.of(9, 10, 11)));
  }

}