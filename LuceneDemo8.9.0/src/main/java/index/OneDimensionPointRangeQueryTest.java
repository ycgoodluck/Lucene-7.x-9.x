package index;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Random;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.BytesRef;
import util.FileOperation;

/**
 * @author Lu Xugang
 * @date 2021/6/18 9:46 上午
 */
public class OneDimensionPointRangeQueryTest {
	private Directory directory;

	{
		try {
			FileOperation.deleteFile("./data");
			directory = new MMapDirectory(Paths.get("./data"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Analyzer analyzer = new WhitespaceAnalyzer();
	private IndexWriterConfig conf = new IndexWriterConfig(analyzer);
	private IndexWriter indexWriter;

	public void doSearch() throws Exception {
		conf.setUseCompoundFile(false);
		indexWriter = new IndexWriter(directory, conf);

		Random random = new Random();
		Document doc;
		// 文档0
		doc = new Document();
		doc.add(new StringField("标题", "balabala", Field.Store.YES));
		doc.add(new StringField("内容", "balabala", Field.Store.YES));
		doc.add(new StringField("附件", "balabala", Field.Store.YES));
		indexWriter.addDocument(doc);
		// 文档1
		doc = new Document();
		doc.add(new IntPoint("sortField", 2));
		doc.add(new NumericDocValuesField("sortField", 2));
		doc.add(new BinaryDocValuesField("sortFieldString", new BytesRef("b")));
		indexWriter.addDocument(doc);
		//        // 文档2
		//        doc = new Document();
		//        doc.add(new TextField("abc", "edd", Field.Store.YES ));
		//        indexWriter.addDocument(doc);
		int count = 0;
		int a;
		while (count++ < 40960) {
			doc = new Document();
			a = random.nextInt(100);
			a = a <= 2 ? a + 4 : a;
			doc.add(new IntPoint("sortField", a));
			doc.add(new NumericDocValuesField("sortField", a));
			doc.add(new BinaryDocValuesField("sortFieldString", new BytesRef(String.valueOf(a))));
			indexWriter.addDocument(doc);
		}
		indexWriter.commit();
		DirectoryReader reader = DirectoryReader.open(indexWriter);
		IndexSearcher searcher = new IndexSearcher(reader);
		int[] lowValue = {-1};
		int[] upValue = {70};
		Query query = IntPoint.newRangeQuery("sortField", lowValue, upValue);

		// 返回Top5的结果
		int resultTopN = 5;

		//        SortField sortField = new SortedNumericSortField("sortField", SortField.Type.INT);
		SortField sortField = new SortField("sortFieldString", SortField.Type.SCORE);
		sortField.setCanUsePoints();
		Sort sort = new Sort(sortField);
		TopFieldCollector collector = TopFieldCollector.create(sort, resultTopN, 520);
		searcher.search(new MatchAllDocsQuery(), collector);
		ScoreDoc[] scoreDocs = collector.topDocs().scoreDocs;
		for (ScoreDoc scoreDoc : scoreDocs) {
			System.out.println("文档号: " + scoreDoc.doc + "");
		}

		boolean isEarlyTerminal = collector.isEarlyTerminated();
		System.out.println(isEarlyTerminal ? "early exit" : "not early exit");
		System.out.println("totalHits: " + collector.getTotalHits() + "");

		System.out.println("DONE");
	}

	public static void main(String[] args) throws Exception {
		OneDimensionPointRangeQueryTest test = new OneDimensionPointRangeQueryTest();
		test.doSearch();
	}
}
