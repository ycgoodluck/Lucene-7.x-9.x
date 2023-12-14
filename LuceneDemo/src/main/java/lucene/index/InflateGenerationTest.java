package lucene.index;

import io.FileOperation;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * @author Lu Xugang
 * @date 2019/12/4 8:53 下午
 */
public class InflateGenerationTest {
	private Directory directory;
	private Analyzer analyzer = new WhitespaceAnalyzer();
	private IndexWriterConfig oldConf = new IndexWriterConfig(analyzer);
	private IndexWriter indexWriter;


	{
		try {
			FileOperation.deleteFile("./data");
			directory = FSDirectory.open(Paths.get("./data"));
			oldConf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
			oldConf.setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE);
			oldConf.setMergePolicy(NoMergePolicy.INSTANCE);
			oldConf.setCommitOnClose(false);
			indexWriter = new IndexWriter(directory, oldConf);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void doIndex() throws Exception {

		FieldType type = new FieldType();
		type.setStored(true);
		type.setStoreTermVectors(true);
		type.setStoreTermVectorPositions(true);
		type.setStoreTermVectorPayloads(true);
		type.setStoreTermVectorOffsets(true);
		type.setTokenized(true);
		type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);

		Document doc;
		// 文档0
		doc = new Document();
		doc.add(new StringField("author", "Lily", Field.Store.YES));
		doc.add(new NumericDocValuesField("age", -2));
		indexWriter.addDocument(doc);
		// 文档1
		doc = new Document();
		doc.add(new StringField("author", "Luxugang", Field.Store.YES));
		doc.add(new NumericDocValuesField("age", 0));
		indexWriter.addDocument(doc);
		// 文档2
		doc = new Document();
		doc.add(new StringField("author", "Jay", Field.Store.YES));
		doc.add(new NumericDocValuesField("age", 0));
		indexWriter.addDocument(doc);
		// 生成segments_1
		indexWriter.commit();

		// 第一次更新以_0为前缀的段的DocValues的信息
		indexWriter.updateNumericDocValue(new Term("author", "Lily"), "age", 2);
		// 生成segments_2
		indexWriter.commit();

		// 第二次更新以_0为前缀的段的DocValues的信息
		indexWriter.updateNumericDocValue(new Term("author", "Lily"), "age", 3);
		// 生成segments_3
		indexWriter.commit();

		System.out.println("hah");
	}

	public static void main(String[] args) throws Exception {
		InflateGenerationTest inflateGenerationTest = new InflateGenerationTest();
		inflateGenerationTest.doIndex();
	}
}
