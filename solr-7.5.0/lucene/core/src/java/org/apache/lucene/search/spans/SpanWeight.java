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
package org.apache.lucene.search.spans;


import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.MatchesUtils;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.Similarity.SimScorer;

/**
 * Expert-only.  Public for use by other weight implementations
 */
public abstract class SpanWeight extends Weight {

	/**
	 * Enumeration defining what postings information should be retrieved from the
	 * index for a given Spans
	 */
	public enum Postings {
		POSITIONS {
			@Override
			public int getRequiredPostings() {
				return PostingsEnum.POSITIONS;
			}
		},
		PAYLOADS {
			@Override
			public int getRequiredPostings() {
				return PostingsEnum.PAYLOADS;
			}
		},
		OFFSETS {
			@Override
			public int getRequiredPostings() {
				return PostingsEnum.PAYLOADS | PostingsEnum.OFFSETS;
			}
		};

		public abstract int getRequiredPostings();

		public Postings atLeast(Postings postings) {
			if (postings.compareTo(this) > 0)
				return postings;
			return this;
		}
	}

	protected final Similarity similarity;
	protected final Similarity.SimWeight simWeight;
	protected final String field;

	/**
	 * Create a new SpanWeight
	 *
	 * @param query        the parent query
	 * @param searcher     the IndexSearcher to query against
	 * @param termContexts a map of terms to termcontexts for use in building the similarity.  May
	 *                     be null if scores are not required
	 * @throws IOException on error
	 */
	public SpanWeight(SpanQuery query, IndexSearcher searcher, Map<Term, TermContext> termContexts, float boost) throws IOException {
		super(query);
		this.field = query.getField();
		this.similarity = searcher.getSimilarity(termContexts != null);
		this.simWeight = buildSimWeight(query, searcher, termContexts, boost);
	}

	private Similarity.SimWeight buildSimWeight(SpanQuery query, IndexSearcher searcher, Map<Term, TermContext> termContexts, float boost) throws IOException {
		if (termContexts == null || termContexts.size() == 0 || query.getField() == null)
			return null;
		TermStatistics[] termStats = new TermStatistics[termContexts.size()];
		int i = 0;
		for (Term term : termContexts.keySet()) {
			termStats[i] = searcher.termStatistics(term, termContexts.get(term));
			i++;
		}
		CollectionStatistics collectionStats = searcher.collectionStatistics(query.getField());
		return similarity.computeWeight(boost, collectionStats, termStats);
	}

	/**
	 * Collect all TermContexts used by this Weight
	 *
	 * @param contexts a map to add the TermContexts to
	 */
	public abstract void extractTermContexts(Map<Term, TermContext> contexts);

	/**
	 * Expert: Return a Spans object iterating over matches from this Weight
	 *
	 * @param ctx a LeafReaderContext for this Spans
	 * @return a Spans
	 * @throws IOException on error
	 */
	public abstract Spans getSpans(LeafReaderContext ctx, Postings requiredPostings) throws IOException;

	@Override
	public SpanScorer scorer(LeafReaderContext context) throws IOException {
		final Spans spans = getSpans(context, Postings.POSITIONS);
		if (spans == null) {
			return null;
		}
		final Similarity.SimScorer docScorer = getSimScorer(context);
		return new SpanScorer(this, spans, docScorer);
	}

	/**
	 * Return a SimScorer for this context
	 *
	 * @param context the LeafReaderContext
	 * @return a SimWeight
	 * @throws IOException on error
	 */
	public Similarity.SimScorer getSimScorer(LeafReaderContext context) throws IOException {
		return simWeight == null ? null : similarity.simScorer(simWeight, context);
	}

	@Override
	public Explanation explain(LeafReaderContext context, int doc) throws IOException {
		SpanScorer scorer = scorer(context);
		if (scorer != null) {
			int newDoc = scorer.iterator().advance(doc);
			if (newDoc == doc) {
				float freq = scorer.sloppyFreq();
				SimScorer docScorer = similarity.simScorer(simWeight, context);
				Explanation freqExplanation = Explanation.match(freq, "phraseFreq=" + freq);
				Explanation scoreExplanation = docScorer.explain(doc, freqExplanation);
				return Explanation.match(scoreExplanation.getValue(),
					"weight(" + getQuery() + " in " + doc + ") [" + similarity.getClass().getSimpleName() + "], result of:",
					scoreExplanation);
			}
		}

		return Explanation.noMatch("no matching term");
	}

	private static class TermMatch {
		Term term;
		int position;
		int startOffset;
		int endOffset;
	}

	@Override
	public Matches matches(LeafReaderContext context, int doc) throws IOException {
		return MatchesUtils.forField(field, () -> {
			Spans spans = getSpans(context, Postings.OFFSETS);
			if (spans == null || spans.advance(doc) != doc) {
				return null;
			}
			return new MatchesIterator() {

				int innerTermCount = 0;
				TermMatch[] innerTerms = new TermMatch[0];

				SpanCollector termCollector = new SpanCollector() {
					@Override
					public void collectLeaf(PostingsEnum postings, int position, Term term) throws IOException {
						innerTermCount++;
						if (innerTermCount > innerTerms.length) {
							TermMatch[] temp = new TermMatch[innerTermCount];
							System.arraycopy(innerTerms, 0, temp, 0, innerTermCount - 1);
							innerTerms = temp;
							innerTerms[innerTermCount - 1] = new TermMatch();
						}
						innerTerms[innerTermCount - 1].term = term;
						innerTerms[innerTermCount - 1].position = position;
						innerTerms[innerTermCount - 1].startOffset = postings.startOffset();
						innerTerms[innerTermCount - 1].endOffset = postings.endOffset();
					}

					@Override
					public void reset() {
						innerTermCount = 0;
					}
				};

				@Override
				public boolean next() throws IOException {
					innerTermCount = 0;
					return spans.nextStartPosition() != Spans.NO_MORE_POSITIONS;
				}

				@Override
				public int startPosition() {
					return spans.startPosition();
				}

				@Override
				public int endPosition() {
					return spans.endPosition() - 1;
				}

				@Override
				public int startOffset() throws IOException {
					if (innerTermCount == 0) {
						collectInnerTerms();
					}
					return innerTerms[0].startOffset;
				}

				@Override
				public int endOffset() throws IOException {
					if (innerTermCount == 0) {
						collectInnerTerms();
					}
					return innerTerms[innerTermCount - 1].endOffset;
				}

				@Override
				public MatchesIterator getSubMatches() throws IOException {
					if (innerTermCount == 0) {
						collectInnerTerms();
					}
					return new MatchesIterator() {

						int upto = -1;

						@Override
						public boolean next() throws IOException {
							upto++;
							return upto < innerTermCount;
						}

						@Override
						public int startPosition() {
							return innerTerms[upto].position;
						}

						@Override
						public int endPosition() {
							return innerTerms[upto].position;
						}

						@Override
						public int startOffset() throws IOException {
							return innerTerms[upto].startOffset;
						}

						@Override
						public int endOffset() throws IOException {
							return innerTerms[upto].endOffset;
						}

						@Override
						public MatchesIterator getSubMatches() throws IOException {
							return null;
						}

						@Override
						public Query getQuery() {
							return new TermQuery(innerTerms[upto].term);
						}
					};
				}

				@Override
				public Query getQuery() {
					return SpanWeight.this.getQuery();
				}

				void collectInnerTerms() throws IOException {
					termCollector.reset();
					spans.collect(termCollector);
					Arrays.sort(innerTerms, 0, innerTermCount, Comparator.comparing(a -> a.position));
				}
			};
		});
	}
}
