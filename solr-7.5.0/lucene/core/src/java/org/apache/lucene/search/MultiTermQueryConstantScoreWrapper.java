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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.DocIdSetBuilder;

/**
 * This class also provides the functionality behind
 * {@link MultiTermQuery#CONSTANT_SCORE_REWRITE}.
 * It tries to rewrite per-segment as a boolean query
 * that returns a constant score and otherwise fills a
 * bit set with matches and builds a Scorer on top of
 * this bit set.
 */
final class MultiTermQueryConstantScoreWrapper<Q extends MultiTermQuery> extends Query {

	// mtq that matches 16 terms or less will be executed as a regular disjunction
	private static final int BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD = 16;

	private static class TermAndState {
		final BytesRef term;
		final TermState state;
		final int docFreq;
		final long totalTermFreq;

		TermAndState(BytesRef term, TermState state, int docFreq, long totalTermFreq) {
			this.term = term;
			this.state = state;
			this.docFreq = docFreq;
			this.totalTermFreq = totalTermFreq;
		}
	}

	private static class WeightOrDocIdSet {
		final Weight weight;
		final DocIdSet set;

		WeightOrDocIdSet(Weight weight) {
			this.weight = Objects.requireNonNull(weight);
			this.set = null;
		}

		WeightOrDocIdSet(DocIdSet bitset) {
			this.set = bitset;
			this.weight = null;
		}
	}

	protected final Q query;

	/**
	 * Wrap a {@link MultiTermQuery} as a Filter.
	 */
	protected MultiTermQueryConstantScoreWrapper(Q query) {
		this.query = query;
	}

	@Override
	public String toString(String field) {
		// query.toString should be ok for the filter, too, if the query boost is 1.0f
		return query.toString(field);
	}

	@Override
	public final boolean equals(final Object other) {
		return sameClassAs(other) &&
			query.equals(((MultiTermQueryConstantScoreWrapper<?>) other).query);
	}

	@Override
	public final int hashCode() {
		return 31 * classHash() + query.hashCode();
	}

	/**
	 * Returns the encapsulated query
	 */
	public Q getQuery() {
		return query;
	}

	/**
	 * Returns the field name for this query
	 */
	public final String getField() {
		return query.getField();
	}

	@Override
	public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
		return new ConstantScoreWeight(this, boost) {

			/** Try to collect terms from the given terms enum and return true iff all
			 *  terms could be collected. If {@code false} is returned, the enum is
			 *  left positioned on the next term. */
			// 收集每一个域值的信息
			private boolean collectTerms(LeafReaderContext context, TermsEnum termsEnum, List<TermAndState> terms) throws IOException {
				// 这里的域值表示BooleanQuery的Clause个数最多只能是BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD
				// BooleanQuery.getMaxClauseCount()默认最大值是1024
				final int threshold = Math.min(BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD, BooleanQuery.getMaxClauseCount());
				for (int i = 0; i < threshold; ++i) {
					final BytesRef term = termsEnum.next();
					if (term == null) {
						return true;
					}
					TermState state = termsEnum.termState();
					terms.add(new TermAndState(BytesRef.deepCopyOf(term), state, termsEnum.docFreq(), termsEnum.totalTermFreq()));
				}
				// 说明当前term的总个数超过阈值，那么返回false
				return termsEnum.next() == null;
			}

			/**
			 * On the given leaf context, try to either rewrite to a disjunction if
			 * there are few terms, or build a bitset containing matching docs.
			 */
			private WeightOrDocIdSet rewrite(LeafReaderContext context) throws IOException {
				// 获得一个域名的所有域值信息
				final Terms terms = context.reader().terms(query.field);
				if (terms == null) {
					// field does not exist
					return new WeightOrDocIdSet((DocIdSet) null);
				}

				final TermsEnum termsEnum = query.getTermsEnum(terms);
				assert termsEnum != null;

				PostingsEnum docs = null;

				final List<TermAndState> collectedTerms = new ArrayList<>();
				// if 语句为真，说明term的个数没有超过16 (BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD)
				if (collectTerms(context, termsEnum, collectedTerms)) {
					// build a boolean query
					BooleanQuery.Builder bq = new BooleanQuery.Builder();
					for (TermAndState t : collectedTerms) {
						final TermContext termContext = new TermContext(searcher.getTopReaderContext());
						// 获得每一个term的数据
						termContext.register(t.state, context.ord, t.docFreq, t.totalTermFreq);
						// 注意这里的Occur的值是SHOULD
						bq.add(new TermQuery(new Term(query.field, t.term), termContext), Occur.SHOULD);
					}
					// 封装为ConstantScoreQuery对象说明，满足要求的文档的打分都会被值为一样的值（1）
					Query q = new ConstantScoreQuery(bq.build());
					// 这里再次执行的了重写query，创建weight对象的操作
					// 实际执行了 BooleanQuery的rewrite()跟createWeight()方法
					final Weight weight = searcher.rewrite(q).createWeight(searcher, needsScores, score());
					return new WeightOrDocIdSet(weight);
				}

				// Too many terms: go back to the terms we already collected and start building the bit set
				// query.field这个域名包含的域值个数超过阈值
				DocIdSetBuilder builder = new DocIdSetBuilder(context.reader().maxDoc(), terms);
				if (collectedTerms.isEmpty() == false) {
					TermsEnum termsEnum2 = terms.iterator();
					for (TermAndState t : collectedTerms) {
						termsEnum2.seekExact(t.term, t.state);
						docs = termsEnum2.postings(docs, PostingsEnum.NONE);
						builder.add(docs);
					}
				}

				// Then keep filling the bit set with remaining terms
				do {
					docs = termsEnum.postings(docs, PostingsEnum.NONE);
					builder.add(docs);
				} while (termsEnum.next() != null);

				return new WeightOrDocIdSet(builder.build());
			}

			private Scorer scorer(DocIdSet set) throws IOException {
				if (set == null) {
					return null;
				}
				final DocIdSetIterator disi = set.iterator();
				if (disi == null) {
					return null;
				}
				return new ConstantScoreScorer(this, score(), disi);
			}

			@Override
			public BulkScorer bulkScorer(LeafReaderContext context) throws IOException {
				final WeightOrDocIdSet weightOrBitSet = rewrite(context);
				if (weightOrBitSet.weight != null) {
					return weightOrBitSet.weight.bulkScorer(context);
				} else {
					final Scorer scorer = scorer(weightOrBitSet.set);
					if (scorer == null) {
						return null;
					}
					return new DefaultBulkScorer(scorer);
				}
			}

			@Override
			public Matches matches(LeafReaderContext context, int doc) throws IOException {
				final Terms terms = context.reader().terms(query.field);
				if (terms == null) {
					return null;
				}
				if (terms.hasPositions() == false) {
					return super.matches(context, doc);
				}
				return MatchesUtils.forField(query.field, () -> DisjunctionMatchesIterator.fromTermsEnum(context, doc, query, query.field, query.getTermsEnum(terms)));
			}

			@Override
			public Scorer scorer(LeafReaderContext context) throws IOException {
				final WeightOrDocIdSet weightOrBitSet = rewrite(context);
				if (weightOrBitSet.weight != null) {
					return weightOrBitSet.weight.scorer(context);
				} else {
					return scorer(weightOrBitSet.set);
				}
			}

			@Override
			public boolean isCacheable(LeafReaderContext ctx) {
				return true;
			}

		};
	}
}
