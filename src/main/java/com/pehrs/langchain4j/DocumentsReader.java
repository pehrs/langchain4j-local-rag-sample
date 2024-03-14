package com.pehrs.langchain4j;

import dev.langchain4j.data.document.Document;
import java.util.stream.Stream;

public interface DocumentsReader {

  Stream<Document> readDocuments();

}
