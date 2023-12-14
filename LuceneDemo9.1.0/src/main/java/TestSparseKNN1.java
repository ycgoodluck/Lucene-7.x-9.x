import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.KnnVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnVectorQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class TestSparseKNN1 {

	public Cost doSearch() throws Exception {
		FileOperation.deleteFile("./data");
		Directory directory = new MMapDirectory(Path.of("./data"));
		Analyzer analyzer = new StandardAnalyzer();
		IndexWriterConfig conf = new IndexWriterConfig(analyzer);
		conf.setUseCompoundFile(false);
		Document doc;

		FieldType fieldType = new FieldType();
		fieldType.setIndexOptions(IndexOptions.DOCS);
		fieldType.setStored(true);

		int dimension = 3;
		int number = 100000;
		String input = "./dimension/vector." + dimension + "d." + (number / 1000) + "k.txt";
		IndexWriter indexWriter = new IndexWriter(directory, conf);
		long indexCost = 0;
		int count = 0;
		System.out.println("start to index");
		long indexStart = System.currentTimeMillis();
		try (Scanner sc = new Scanner(new FileReader(input))) {
			while (sc.hasNextLine()) {
				count++;
				doc = new Document();
				String[] lineStr = sc.nextLine().split(" ");
				if (lineStr.length == 0) {
					doc.add(new Field("Content", "a", fieldType));
				} else {
					float[] newFloat = new float[dimension];
					for (int i = 0; i < dimension; i++) {
						newFloat[i] = Float.parseFloat(lineStr[i]);
					}
					// make vector docIds sparse
					if (count % 2 == 0 || count % 3 == 0) {
						doc.add(new KnnVectorField("vector", newFloat));
					} else {
						doc.add(new Field("Content", String.valueOf(count), fieldType));
					}
				}
				indexWriter.addDocument(doc);

			}
			doc = new Document();
			doc.add(new Field("Content", "a", fieldType));
			indexWriter.addDocument(doc);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		indexWriter.commit();
		long indexEnd = System.currentTimeMillis();
		// index cost
		indexCost = indexEnd - indexStart;

		DirectoryReader reader = DirectoryReader.open(indexWriter);
		IndexSearcher indexSearcher = new IndexSearcher(reader);

		List<float[]> candidateQueryPara = new ArrayList<>();
		candidateQueryPara.add(new float[]{0, 0, 0});
		candidateQueryPara.add(new float[]{-0.18344f, 0.95567f, -0.46423f});
		candidateQueryPara.add(new float[]{-0.25966f, -0.15258f, -0.5823f});
		candidateQueryPara.add(new float[]{0.11318f, -1.0546f, 1.2285f});
		candidateQueryPara.add(new float[]{0.42921f, 0.60283f, -0.21487f});
		candidateQueryPara.add(new float[]{1.6214f, 0.15795f, 0.037353f});
		candidateQueryPara.add(new float[]{-0.044981f, 0.05041f, -0.44968f});
		candidateQueryPara.add(new float[]{-0.2479f, 0.1701f, 0.032121f});
		candidateQueryPara.add(new float[]{0.016738f, 0.21394f, -0.16618f});
		candidateQueryPara.add(new float[]{1f, 1f, 1f});

		long costSum = 0;
		int NumberOfDocumentsToFind = 10;
		for (float[] floats : candidateQueryPara) {
			KnnVectorQuery kvq = new KnnVectorQuery("vector", floats, NumberOfDocumentsToFind);
			long searchStart = System.currentTimeMillis();
			indexSearcher.search(kvq, number);
			long searchEnd = System.currentTimeMillis();
			long cost = searchEnd - searchStart;
			costSum += cost;
		}
		indexWriter.close();
		return new Cost(indexCost, costSum / candidateQueryPara.size(), NumberOfDocumentsToFind, reader.maxDoc());
	}

	public static void main(String[] args) throws Exception {
		TestSparseKNN1 TestSparseKNN1 = new TestSparseKNN1();
		int iter = 1;
		long searchCostSum = 0;
		long indexCostSum = 0;
		int top = 0;
		int maxDoc = 0;
		for (int i = 0; i < iter; i++) {
			System.out.println("iteration: " + i + "");
			Cost cost = TestSparseKNN1.doSearch();
			System.out.println("per index cost: " + cost.indexCost + "");
			System.out.println("per search cost: " + cost.searchCost + "");
			System.out.println("-------------------------------");
			searchCostSum += cost.searchCost;
			indexCostSum += cost.indexCost;
			top = cost.NumberOfDocumentsToFind;
			maxDoc = cost.maxDoc;
		}
		System.out.println("avg of " + iter + " index cost: " + (indexCostSum / iter) + "ms");
		System.out.println("avg of " + iter + " search cost: " + (searchCostSum / iter) + "ms");
		System.out.println("number of documents to find: " + top + "");
		System.out.println("maxDoc: " + maxDoc + "");
		System.out.println("DONE");
	}

	public static class Cost {
		private final long indexCost;
		private final long searchCost;
		private final int NumberOfDocumentsToFind;
		private final int maxDoc;

		public Cost(long indexCost, long searchCost, int NumberOfDocumentsToFind, int maxDoc) {
			this.indexCost = indexCost;
			this.searchCost = searchCost;
			this.NumberOfDocumentsToFind = NumberOfDocumentsToFind;
			this.maxDoc = maxDoc;
		}
	}
}
