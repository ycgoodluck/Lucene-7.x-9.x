package lucene.query;

import io.FileOperation;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * @author Lu Xugang
 * @date 2019-04-15 13:03
 */
public class TermQuerySHOULDMUSTTest {

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


		Document doc;
		// 0

		int count = 0;
		while (count++ < 7999) {
			doc = new Document();
			doc.add(new TextField("content", "a e c", Field.Store.YES));
			indexWriter.addDocument(doc);
			// 1
			doc = new Document();
			doc.add(new TextField("content", "e", Field.Store.YES));
			indexWriter.addDocument(doc);
			// 2
			doc = new Document();
			doc.add(new TextField("content", "c", Field.Store.YES));
			indexWriter.addDocument(doc);
			// 3
			doc = new Document();
			doc.add(new TextField("content", "a c e", Field.Store.YES));
			indexWriter.addDocument(doc);
			// 4
			doc = new Document();
			doc.add(new TextField("content", "h", Field.Store.YES));
			indexWriter.addDocument(doc);
			// 5
			doc = new Document();
			doc.add(new TextField("content", "b h", Field.Store.YES));
			indexWriter.addDocument(doc);
			// 6
			doc = new Document();
			doc.add(new TextField("content", "c a", Field.Store.YES));
			indexWriter.addDocument(doc);
			// 7
			doc = new Document();
			doc.add(new TextField("content", "a e h", Field.Store.YES));
			indexWriter.addDocument(doc);
			// 8
			doc = new Document();
			doc.add(new TextField("content", "b c d e h e", Field.Store.YES));
			indexWriter.addDocument(doc);
			// 9
			doc = new Document();
			doc.add(new TextField("content", "a e a b ", Field.Store.YES));
			indexWriter.addDocument(doc);
		}
		indexWriter.commit();

		IndexReader reader = DirectoryReader.open(indexWriter);
		IndexSearcher searcher = new IndexSearcher(reader);

		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		builder.add(new TermQuery(new Term("content", "a")), BooleanClause.Occur.SHOULD);
		builder.add(new TermQuery(new Term("content", "b")), BooleanClause.Occur.SHOULD);
		builder.add(new TermQuery(new Term("content", "d")), BooleanClause.Occur.SHOULD);
		builder.add(new TermQuery(new Term("content", "c")), BooleanClause.Occur.MUST);
		builder.add(new TermQuery(new Term("content", "e")), BooleanClause.Occur.MUST);
		builder.add(new TermQuery(new Term("content", "h")), BooleanClause.Occur.MUST);
		builder.setMinimumNumberShouldMatch(2);
		Query query = builder.build();

		int topN = 10;

		ScoreDoc[] scoreDocs = searcher.search(query, topN).scoreDocs;


		System.out.println("Total Result Number: " + scoreDocs.length + "");
		for (int i = 0; i < scoreDocs.length; i++) {
			ScoreDoc scoreDoc = scoreDocs[i];
//            // 输出满足查询条件的 文档号
			System.out.println("result" + i + ": 文档" + scoreDoc.doc + ", " + scoreDoc.score + "");
		}
	}

	public static void main(String[] args) throws Exception {
		TermQuerySHOULDMUSTTest test = new TermQuerySHOULDMUSTTest();
		test.doSearch();
	}
}
