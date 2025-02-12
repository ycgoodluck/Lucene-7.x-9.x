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
package org.apache.lucene.classification;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.classification.utils.NearestFuzzyQuery;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;

/**
 * A k-Nearest Neighbor classifier based on {@link NearestFuzzyQuery}.
 *
 * @lucene.experimental
 */
public class KNearestFuzzyClassifier implements Classifier<BytesRef> {

	/**
	 * the name of the fields used as the input text
	 */
	private final String[] textFieldNames;

	/**
	 * the name of the field used as the output text
	 */
	private final String classFieldName;

	/**
	 * an {@link IndexSearcher} used to perform queries
	 */
	private final IndexSearcher indexSearcher;

	/**
	 * the no. of docs to compare in order to find the nearest neighbor to the input text
	 */
	private final int k;

	/**
	 * a {@link Query} used to filter the documents that should be used from this classifier's underlying {@link LeafReader}
	 */
	private final Query query;
	private final Analyzer analyzer;

	/**
	 * Creates a {@link KNearestFuzzyClassifier}.
	 *
	 * @param indexReader    the reader on the index to be used for classification
	 * @param analyzer       an {@link Analyzer} used to analyze unseen text
	 * @param similarity     the {@link Similarity} to be used by the underlying {@link IndexSearcher} or {@code null}
	 *                       (defaults to {@link BM25Similarity})
	 * @param query          a {@link Query} to eventually filter the docs used for training the classifier, or {@code null}
	 *                       if all the indexed docs should be used
	 * @param k              the no. of docs to select in the MLT results to find the nearest neighbor
	 * @param classFieldName the name of the field used as the output for the classifier
	 * @param textFieldNames the name of the fields used as the inputs for the classifier, they can contain boosting indication e.g. title^10
	 */
	public KNearestFuzzyClassifier(IndexReader indexReader, Similarity similarity, Analyzer analyzer, Query query, int k,
																 String classFieldName, String... textFieldNames) {
		this.textFieldNames = textFieldNames;
		this.classFieldName = classFieldName;
		this.analyzer = analyzer;
		this.indexSearcher = new IndexSearcher(indexReader);
		if (similarity != null) {
			this.indexSearcher.setSimilarity(similarity);
		} else {
			this.indexSearcher.setSimilarity(new BM25Similarity());
		}
		this.query = query;
		this.k = k;
	}


	@Override
	public ClassificationResult<BytesRef> assignClass(String text) throws IOException {
		TopDocs knnResults = knnSearch(text);
		List<ClassificationResult<BytesRef>> assignedClasses = buildListFromTopDocs(knnResults);
		ClassificationResult<BytesRef> assignedClass = null;
		double maxscore = -Double.MAX_VALUE;
		for (ClassificationResult<BytesRef> cl : assignedClasses) {
			if (cl.getScore() > maxscore) {
				assignedClass = cl;
				maxscore = cl.getScore();
			}
		}
		return assignedClass;
	}

	@Override
	public List<ClassificationResult<BytesRef>> getClasses(String text) throws IOException {
		TopDocs knnResults = knnSearch(text);
		List<ClassificationResult<BytesRef>> assignedClasses = buildListFromTopDocs(knnResults);
		Collections.sort(assignedClasses);
		return assignedClasses;
	}

	@Override
	public List<ClassificationResult<BytesRef>> getClasses(String text, int max) throws IOException {
		TopDocs knnResults = knnSearch(text);
		List<ClassificationResult<BytesRef>> assignedClasses = buildListFromTopDocs(knnResults);
		Collections.sort(assignedClasses);
		return assignedClasses.subList(0, max);
	}

	private TopDocs knnSearch(String text) throws IOException {
		BooleanQuery.Builder bq = new BooleanQuery.Builder();
		NearestFuzzyQuery nearestFuzzyQuery = new NearestFuzzyQuery(analyzer);
		for (String fieldName : textFieldNames) {
			nearestFuzzyQuery.addTerms(text, fieldName);
		}
		bq.add(nearestFuzzyQuery, BooleanClause.Occur.MUST);
		Query classFieldQuery = new WildcardQuery(new Term(classFieldName, "*"));
		bq.add(new BooleanClause(classFieldQuery, BooleanClause.Occur.MUST));
		if (query != null) {
			bq.add(query, BooleanClause.Occur.MUST);
		}
		return indexSearcher.search(bq.build(), k);
	}

	/**
	 * build a list of classification results from search results
	 *
	 * @param topDocs the search results as a {@link TopDocs} object
	 * @return a {@link List} of {@link ClassificationResult}, one for each existing class
	 * @throws IOException if it's not possible to get the stored value of class field
	 */
	private List<ClassificationResult<BytesRef>> buildListFromTopDocs(TopDocs topDocs) throws IOException {
		Map<BytesRef, Integer> classCounts = new HashMap<>();
		Map<BytesRef, Double> classBoosts = new HashMap<>(); // this is a boost based on class ranking positions in topDocs
		float maxScore = topDocs.getMaxScore();
		for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
			IndexableField storableField = indexSearcher.doc(scoreDoc.doc).getField(classFieldName);
			if (storableField != null) {
				BytesRef cl = new BytesRef(storableField.stringValue());
				//update count
				classCounts.merge(cl, 1, (a, b) -> a + b);
				//update boost, the boost is based on the best score
				Double totalBoost = classBoosts.get(cl);
				double singleBoost = scoreDoc.score / maxScore;
				if (totalBoost != null) {
					classBoosts.put(cl, totalBoost + singleBoost);
				} else {
					classBoosts.put(cl, singleBoost);
				}
			}
		}
		List<ClassificationResult<BytesRef>> returnList = new ArrayList<>();
		List<ClassificationResult<BytesRef>> temporaryList = new ArrayList<>();
		int sumdoc = 0;
		for (Map.Entry<BytesRef, Integer> entry : classCounts.entrySet()) {
			Integer count = entry.getValue();
			Double normBoost = classBoosts.get(entry.getKey()) / count; //the boost is normalized to be 0<b<1
			temporaryList.add(new ClassificationResult<>(entry.getKey().clone(), (count * normBoost) / (double) k));
			sumdoc += count;
		}

		//correction
		if (sumdoc < k) {
			for (ClassificationResult<BytesRef> cr : temporaryList) {
				returnList.add(new ClassificationResult<>(cr.getAssignedClass(), cr.getScore() * k / (double) sumdoc));
			}
		} else {
			returnList = temporaryList;
		}
		return returnList;
	}

	@Override
	public String toString() {
		return "KNearestFuzzyClassifier{" +
			"textFieldNames=" + Arrays.toString(textFieldNames) +
			", classFieldName='" + classFieldName + '\'' +
			", k=" + k +
			", query=" + query +
			", similarity=" + indexSearcher.getSimilarity(true) +
			'}';
	}
}
