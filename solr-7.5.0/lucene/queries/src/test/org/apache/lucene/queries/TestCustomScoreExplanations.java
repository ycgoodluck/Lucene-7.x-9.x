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
package org.apache.lucene.queries;

import java.io.IOException;

import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionQuery;
import org.apache.lucene.queries.function.valuesource.ConstValueSource;
import org.apache.lucene.search.BaseExplanationTestCase;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;

public class TestCustomScoreExplanations extends BaseExplanationTestCase {
	public void testOneTerm() throws Exception {
		Query q = new TermQuery(new Term(FIELD, "w1"));
		CustomScoreQuery csq = new CustomScoreQuery(q, new FunctionQuery(new ConstValueSource(5)));
		qtest(csq, new int[]{0, 1, 2, 3});
	}

	public void testBoost() throws Exception {
		Query q = new TermQuery(new Term(FIELD, "w1"));
		CustomScoreQuery csq = new CustomScoreQuery(q, new FunctionQuery(new ConstValueSource(5)));
		qtest(new BoostQuery(csq, 4), new int[]{0, 1, 2, 3});
	}

	public void testTopLevelBoost() throws Exception {
		Query q = new TermQuery(new Term(FIELD, "w1"));
		CustomScoreQuery csq = new CustomScoreQuery(q, new FunctionQuery(new ConstValueSource(5)));
		BooleanQuery.Builder bqB = new BooleanQuery.Builder();
		bqB.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
		bqB.add(csq, BooleanClause.Occur.MUST);
		BooleanQuery bq = bqB.build();
		qtest(new BoostQuery(bq, 6), new int[]{0, 1, 2, 3});
	}

	public void testSubExplanations() throws IOException {
		Query query = new FunctionQuery(new ConstValueSource(5));
		IndexSearcher searcher = newSearcher(BaseExplanationTestCase.searcher.getIndexReader());
		searcher.setSimilarity(new BM25Similarity());

		Explanation expl = searcher.explain(query, 0);
		assertEquals(2, expl.getDetails().length);
		// function
		assertEquals(5f, expl.getDetails()[0].getValue(), 0f);
		// boost
		assertEquals("boost", expl.getDetails()[1].getDescription());
		assertEquals(1f, expl.getDetails()[1].getValue(), 0f);

		query = new BoostQuery(query, 2);
		expl = searcher.explain(query, 0);
		assertEquals(2, expl.getDetails().length);
		// function
		assertEquals(5f, expl.getDetails()[0].getValue(), 0f);
		// boost
		assertEquals("boost", expl.getDetails()[1].getDescription());
		assertEquals(2f, expl.getDetails()[1].getValue(), 0f);

		searcher.setSimilarity(new ClassicSimilarity()); // in order to have a queryNorm != 1
		expl = searcher.explain(query, 0);
		assertEquals(2, expl.getDetails().length);
		// function
		assertEquals(5f, expl.getDetails()[0].getValue(), 0f);
		// boost
		assertEquals("boost", expl.getDetails()[1].getDescription());
		assertEquals(2f, expl.getDetails()[1].getValue(), 0f);
	}
}

