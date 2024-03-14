package com.pehrs.langchain4j.pdf;

import dev.langchain4j.data.document.Document;
import java.io.File;
import org.junit.jupiter.api.Test;

class PdfReaderTest {

  @Test
  public void testDir() {
    PdfDocumentsReader reader = new PdfDocumentsReader(new File("books"));
    Document doc = reader.read();
    System.out.println(doc);
  }

}