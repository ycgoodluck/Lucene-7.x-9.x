package index;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.BytesRef;
import util.FileOperation;

public class DisjunctionMaxQueryTest1 {
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
		FieldType fieldType = new FieldType();
		fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
		fieldType.setStored(true);
		fieldType.setTokenized(true);
		conf.setUseCompoundFile(false);
		conf.setMergeScheduler(new SerialMergeScheduler());
		indexWriter = new IndexWriter(directory, conf);
		Document doc;

		// 文档0
		doc = new Document();
		doc.add(new Field("title", "i love china china", fieldType));
		indexWriter.addDocument(doc);
		indexWriter.commit();
		// 文档1
		doc = new Document();
		doc.add(new Field("body", "china", fieldType));
		indexWriter.addDocument(doc);
		// 文档2
		doc = new Document();
		doc.add(new Field("title", "i love china china", fieldType));
		doc.add(new Field("body", "china", fieldType));
		indexWriter.addDocument(doc);
		indexWriter.commit();

		List<Query> disjunctions = new ArrayList<>();
		TermQuery titleTermQuery = new TermQuery(new Term("title", new BytesRef("china")));
		TermQuery bodyTermQuery = new TermQuery(new Term("body", new BytesRef("china")));

		disjunctions.add(titleTermQuery);
		disjunctions.add(bodyTermQuery);

		DisjunctionMaxQuery disjunctionMaxQuery = new DisjunctionMaxQuery(disjunctions, 0.5f);

		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		builder.add(titleTermQuery, BooleanClause.Occur.SHOULD);
		builder.add(bodyTermQuery, BooleanClause.Occur.SHOULD);
		builder.setMinimumNumberShouldMatch(1);
		BooleanQuery booleanQuery = builder.build();

		DirectoryReader directoryReader = DirectoryReader.open(indexWriter);
		IndexSearcher searcher = new IndexSearcher(directoryReader);

		ScoreDoc[] result = searcher.search(disjunctionMaxQuery, 100).scoreDocs;
		//        ScoreDoc[] result = searcher.search(booleanQuery, 100).scoreDocs;
		for (ScoreDoc scoreDoc : result) {
			System.out.println("文档号: " + scoreDoc.doc + " 文档分数: " + scoreDoc.score + "");
		}

		System.out.println("DONE");
	}

	public static void main(String[] args) throws Exception {
		DisjunctionMaxQueryTest1 test = new DisjunctionMaxQueryTest1();
		test.doSearch();
	}
}
