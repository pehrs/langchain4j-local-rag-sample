package com.pehrs.langchain4j.epub;

import com.amazonaws.util.StringInputStream;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.pehrs.langchain4j.DocumentsReader;
import com.pehrs.langchain4j.RagSample;
import com.typesafe.config.Config;
import dev.langchain4j.data.document.Document;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.xml.bind.DatatypeConverter;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.epub.EpubReader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class EpubDocumentsReader implements DocumentsReader {

  public static final String METADATA_TITLE = "title";

  private final Stack<File> files;

  public static final String PARSE_EPUB_MS = "parse.epub.ms";
  private final Histogram parseHistogram;


  public EpubDocumentsReader(MetricRegistry metricRegistry, Config config) {

    File dir = new File(config.getString("epub.dir"));

    this.files = new Stack();
    this.files.addAll(Arrays.stream(dir.listFiles((file) -> file.getName().endsWith(".epub")))
        .collect(Collectors.toList()));
    this.parseHistogram = metricRegistry.histogram(PARSE_EPUB_MS);
    metricRegistry.register("epub.books.pending",
        (Gauge<Integer>) () -> this.files.size());
  }

  public Document read()  {
    if(files.empty()) {
      return null;
    }

    File file = files.pop();
    try(FileInputStream inputstream = new FileInputStream(file)) {

      long start = System.nanoTime();
      EpubReader reader = new EpubReader();
      Book book = reader.readEpub(inputstream);

      List<Resource> contents = book.getContents();
      List<String> htmlData = contents.stream()
          .filter(res -> !(res.getId().equals("titlepage") || res.getId().equals("id")))
          .map(res -> {
            try {
             // return new String(res.getData(), res.getInputEncoding());
              return new String(res.getData(), "UTF-8");
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          })
          .toList();

      HtmlParser htmlParser = new HtmlParser();
      String txt = htmlData.stream()
          .map(html -> {
            ContentHandler handler = new BodyContentHandler(-1);
            org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
            try {
              htmlParser.parse(new StringInputStream(html.replace("\u00a0", "")), handler, metadata, new ParseContext());
            } catch (IOException | SAXException | TikaException e) {
              throw new RuntimeException(e);
            }
            return handler.toString();
          }).collect(Collectors.joining("\n"));

      String srcId = DatatypeConverter.printHexBinary(file.getName().getBytes(Charset.defaultCharset()));

      dev.langchain4j.data.document.Metadata l4jMetadata = dev.langchain4j.data.document.Metadata.from(
          Map.of(
              Document.FILE_NAME, file.getName(),
              RagSample.METADATA_SRC_ID, srcId,
              METADATA_TITLE, book.getMetadata().getFirstTitle()
          )
      );
      parseHistogram.update(System.nanoTime() - start);
      return Document.document(txt, l4jMetadata);
    } catch (IOException e) {
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
