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
package org.apache.lucene.search;


import java.io.IOException;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.Similarity.SimScorer;

/**
 * A Query that matches documents containing a term. This may be combined with
 * other terms with a {@link BooleanQuery}.
 */
public class TermQuery extends Query {

	private final Term term;
	private final TermContext perReaderTermState;

	final class TermWeight extends Weight {
		private final Similarity similarity;
		// 封装了BM25算法所有信息的对象
		private final Similarity.SimWeight stats;
		private final TermContext termStates;
		private final boolean needsScores;

		public TermWeight(IndexSearcher searcher, boolean needsScores,
											float boost, TermContext termStates) throws IOException {
			super(TermQuery.this);
			if (needsScores && termStates == null) {
				throw new IllegalStateException("termStates are required when scores are needed");
			}
			this.needsScores = needsScores;
			this.termStates = termStates;
			this.similarity = searcher.getSimilarity(needsScores);

			// CollectionStatistics类描述了一个域名的信息，有详细注释
			final CollectionStatistics collectionStats;
			// TermStatistics类描述了一个域值的信息，有详细注释
			final TermStatistics termStats;
			if (needsScores) {
				collectionStats = searcher.collectionStatistics(term.field());
				termStats = searcher.termStatistics(term, termStates);
			} else {
				// we do not need the actual stats, use fake stats with docFreq=maxDoc and ttf=-1
				final int maxDoc = searcher.getIndexReader().maxDoc();
				collectionStats = new CollectionStatistics(term.field(), maxDoc, -1, -1, -1);
				termStats = new TermStatistics(term.bytes(), maxDoc, -1);
			}

			this.stats = similarity.computeWeight(boost, collectionStats, termStats);
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			terms.add(getTerm());
		}

		@Override
		public Matches matches(LeafReaderContext context, int doc) throws IOException {
			TermsEnum te = getTermsEnum(context);
			if (te == null) {
				return null;
			}
			if (context.reader().terms(term.field()).hasPositions() == false) {
				return super.matches(context, doc);
			}
			return MatchesUtils.forField(term.field(), () -> {
				PostingsEnum pe = te.postings(null, PostingsEnum.OFFSETS);
				if (pe.advance(doc) != doc) {
					return null;
				}
				return new TermMatchesIterator(getQuery(), pe);
			});
		}

		@Override
		public String toString() {
			return "weight(" + TermQuery.this + ")";
		}

		@Override
		// Scorer对象用来遍历所有的结果然后给分配分数(打分)
		public Scorer scorer(LeafReaderContext context) throws IOException {
			assert termStates == null || termStates.wasBuiltFor(ReaderUtil.getTopLevelContext(context)) : "The top-reader used to create Weight is not the same as the current reader's top-reader (" + ReaderUtil.getTopLevelContext(context);
			;
			// 某个域名的所有域值的信息
			final TermsEnum termsEnum = getTermsEnum(context);
			if (termsEnum == null) {
				return null;
			}
			// 获得一个域值的倒排表信息
			PostingsEnum docs = termsEnum.postings(null, needsScores ? PostingsEnum.FREQS : PostingsEnum.NONE);
			assert docs != null;
			return new TermScorer(this, docs, similarity.simScorer(stats, context));
		}

		@Override
		public boolean isCacheable(LeafReaderContext ctx) {
			return true;
		}

		/**
		 * Returns a {@link TermsEnum} positioned at this weights Term or null if
		 * the term does not exist in the given context
		 */
		private TermsEnum getTermsEnum(LeafReaderContext context) throws IOException {
			if (termStates != null) {
				// TermQuery either used as a Query or the term states have been provided at construction time
				assert termStates.wasBuiltFor(ReaderUtil.getTopLevelContext(context)) : "The top-reader used to create Weight is not the same as the current reader's top-reader (" + ReaderUtil.getTopLevelContext(context);
				final TermState state = termStates.get(context.ord);
				if (state == null) { // term is not present in that reader
					assert termNotInReader(context.reader(), term) : "no termstate found but term exists in reader term=" + term;
					return null;
				}
				// 根据域名获得TermsEnum对象，封装了所有域值的信息
				final TermsEnum termsEnum = context.reader().terms(term.field()).iterator();
				// 通过seekExact方法来找到 某个域值的信息(TermsEnum状态的改变)
				termsEnum.seekExact(term.bytes(), state);
				return termsEnum;
			} else {
				// TermQuery used as a filter, so the term states have not been built up front
				Terms terms = context.reader().terms(term.field());
				if (terms == null) {
					return null;
				}
				final TermsEnum termsEnum = terms.iterator();
				if (termsEnum.seekExact(term.bytes())) {
					return termsEnum;
				} else {
					return null;
				}
			}
		}

		private boolean termNotInReader(LeafReader reader, Term term) throws IOException {
			// only called from assert
			// System.out.println("TQ.termNotInReader reader=" + reader + " term=" +
			// field + ":" + bytes.utf8ToString());
			return reader.docFreq(term) == 0;
		}

		@Override
		public Explanation explain(LeafReaderContext context, int doc) throws IOException {
			TermScorer scorer = (TermScorer) scorer(context);
			if (scorer != null) {
				int newDoc = scorer.iterator().advance(doc);
				if (newDoc == doc) {
					float freq = scorer.freq();
					SimScorer docScorer = similarity.simScorer(stats, context);
					Explanation freqExplanation = Explanation.match(freq, "termFreq=" + freq);
					Explanation scoreExplanation = docScorer.explain(doc, freqExplanation);
					return Explanation.match(
						scoreExplanation.getValue(),
						"weight(" + getQuery() + " in " + doc + ") ["
							+ similarity.getClass().getSimpleName() + "], result of:",
						scoreExplanation);
				}
			}
			return Explanation.noMatch("no matching term");
		}
	}

	/**
	 * Constructs a query for the term <code>t</code>.
	 */
	public TermQuery(Term t) {
		term = Objects.requireNonNull(t);
		perReaderTermState = null;
	}

	/**
	 * Expert: constructs a TermQuery that will use the provided docFreq instead
	 * of looking up the docFreq against the searcher.
	 */
	public TermQuery(Term t, TermContext states) {
		assert states != null;
		term = Objects.requireNonNull(t);
		perReaderTermState = Objects.requireNonNull(states);
	}

	/**
	 * Returns the term of this query.
	 */
	public Term getTerm() {
		return term;
	}

	@Override
	public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
		final IndexReaderContext context = searcher.getTopReaderContext();
		// 获得一个term的所有信息
		final TermContext termState;
		if (perReaderTermState == null
			|| perReaderTermState.wasBuiltFor(context) == false) {
			if (needsScores) {
				// make TermQuery single-pass if we don't have a PRTS or if the context
				// differs!
				termState = TermContext.build(context, term);
			} else {
				// do not compute the term state, this will help save seeks in the terms
				// dict on segments that have a cache entry for this query
				termState = null;
			}
		} else {
			// PRTS was pre-build for this IS
			// 例子：在TermsQuery中 termState的值就是预先获得的
			termState = this.perReaderTermState;
		}

		return new TermWeight(searcher, needsScores, boost, termState);
	}

	/**
	 * Prints a user-readable version of this query.
	 */
	@Override
	public String toString(String field) {
		StringBuilder buffer = new StringBuilder();
		if (!term.field().equals(field)) {
			buffer.append(term.field());
			buffer.append(":");
		}
		buffer.append(term.text());
		return buffer.toString();
	}

	/**
	 * Returns the {@link TermContext} passed to the constructor, or null if it was not passed.
	 *
	 * @lucene.experimental
	 */
	public TermContext getTermContext() {
		return perReaderTermState;
	}

	/**
	 * Returns true iff <code>other</code> is equal to <code>this</code>.
	 */
	@Override
	public boolean equals(Object other) {
		return sameClassAs(other) &&
			term.equals(((TermQuery) other).term);
	}

	@Override
	public int hashCode() {
		return classHash() ^ term.hashCode();
	}
}
