/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.classification.utils;


import java.io.IOException;
import java.util.Random;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.store.BaseDirectoryWrapper;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Testcase for {@link org.apache.lucene.classification.utils.DatasetSplitter}
 */
public class DataSplitterTest extends LuceneTestCase {

	private LeafReader originalIndex;
	private RandomIndexWriter indexWriter;
	private Directory dir;

	private static final String textFieldName = "text";
	private static final String classFieldName = "class";
	private static final String idFieldName = "id";

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		dir = newDirectory();
		indexWriter = new RandomIndexWriter(random(), dir);

		FieldType ft = new FieldType(TextField.TYPE_STORED);
		ft.setStoreTermVectors(true);
		ft.setStoreTermVectorOffsets(true);
		ft.setStoreTermVectorPositions(true);

		Document doc;
		Random rnd = random();
		for (int i = 0; i < 1000; i++) {
			doc = new Document();
			doc.add(new Field(idFieldName, "id" + Integer.toString(i), ft));
			doc.add(new Field(textFieldName, TestUtil.randomUnicodeString(rnd, 1024), ft));
			String className = Integer.toString(rnd.nextInt(10));
			doc.add(new Field(classFieldName, className, ft));
			doc.add(new SortedDocValuesField(classFieldName, new BytesRef(className)));
			indexWriter.addDocument(doc);
		}

		indexWriter.commit();
		indexWriter.forceMerge(1);

		originalIndex = getOnlyLeafReader(indexWriter.getReader());
	}

	@Override
	@After
	public void tearDown() throws Exception {
		originalIndex.close();
		indexWriter.close();
		dir.close();
		super.tearDown();
	}

	@Test
	public void testSplitOnAllFields() throws Exception {
		assertSplit(originalIndex, 0.1, 0.1);
	}

	@Test
	public void testSplitOnSomeFields() throws Exception {
		assertSplit(originalIndex, 0.2, 0.35, idFieldName, textFieldName);
	}

	public static void assertSplit(LeafReader originalIndex, double testRatio, double crossValidationRatio, String... fieldNames) throws Exception {

		BaseDirectoryWrapper trainingIndex = newDirectory();
		BaseDirectoryWrapper testIndex = newDirectory();
		BaseDirectoryWrapper crossValidationIndex = newDirectory();

		try {
			DatasetSplitter datasetSplitter = new DatasetSplitter(testRatio, crossValidationRatio);
			datasetSplitter.split(originalIndex, trainingIndex, testIndex, crossValidationIndex, new MockAnalyzer(random()), true, classFieldName, fieldNames);

			assertNotNull(trainingIndex);
			assertNotNull(testIndex);
			assertNotNull(crossValidationIndex);

			DirectoryReader trainingReader = DirectoryReader.open(trainingIndex);
			assertEquals((int) (originalIndex.maxDoc() * (1d - testRatio - crossValidationRatio)), trainingReader.maxDoc(), 20);
			DirectoryReader testReader = DirectoryReader.open(testIndex);
			assertEquals((int) (originalIndex.maxDoc() * testRatio), testReader.maxDoc(), 20);
			DirectoryReader cvReader = DirectoryReader.open(crossValidationIndex);
			assertEquals((int) (originalIndex.maxDoc() * crossValidationRatio), cvReader.maxDoc(), 20);

			trainingReader.close();
			testReader.close();
			cvReader.close();
			closeQuietly(trainingReader);
			closeQuietly(testReader);
			closeQuietly(cvReader);
		} finally {
			if (trainingIndex != null) {
				trainingIndex.close();
			}
			if (testIndex != null) {
				testIndex.close();
			}
			if (crossValidationIndex != null) {
				crossValidationIndex.close();
			}
		}
	}

	private static void closeQuietly(IndexReader reader) throws IOException {
		try {
			if (reader != null)
				reader.close();
		} catch (Exception e) {
			// do nothing
		}
	}
}
