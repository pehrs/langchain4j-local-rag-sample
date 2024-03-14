package com.pehrs.langchain4j.pdf;

import com.pehrs.langchain4j.DocumentsReader;
import dev.langchain4j.data.document.Document;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

public class PdfDocumentsReader implements DocumentsReader {

  public static final String METADATA_TITLE = "title";


  private final Stack<File> files;

  public PdfDocumentsReader(File dir) {
    this.files = new Stack();
    this.files.addAll(Arrays.stream(dir.listFiles((file) -> file.getName().endsWith(".pdf")))
        .collect(Collectors.toList()));
  }

  final long GB = 1024L * 1024L * 1024L;

  public Document read()  {
    if(files.empty()) {
      return null;
    }
    File file = files.pop();
    try(FileInputStream inputstream = new FileInputStream(file)) {
      ParseContext pcontext = new ParseContext();
      Metadata metadata = new Metadata();
      PDFParser pdfparser = new PDFParser();
      PDFParserConfig config = new PDFParserConfig();
      config.setMaxMainMemoryBytes(2 * GB);
      pdfparser.setPDFParserConfig(config);
      BodyContentHandler handler = new BodyContentHandler(-1);
      pdfparser.parse(inputstream, handler, metadata, pcontext);

      String text = handler.toString();
      dev.langchain4j.data.document.Metadata l4jMetadata = dev.langchain4j.data.document.Metadata.from(
          Map.of(
              Document.FILE_NAME, file.getName(),
              METADATA_TITLE, metadata.get("dc:title")
          )
      );
      return Document.document(text, l4jMetadata);
    } catch (IOException | TikaException | SAXException e) {
      throw new RuntimeException(e);
    }
  }


  public Iterator<Document> iterator() {
    return new Iterator<Document>() {
      @Override
      public boolean hasNext() {
        return !files.empty();
      }

      @Override
      public Document next() {
        try {
          return read();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  public Stream<Document> readDocuments() {
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED),
        false);
  }
}
