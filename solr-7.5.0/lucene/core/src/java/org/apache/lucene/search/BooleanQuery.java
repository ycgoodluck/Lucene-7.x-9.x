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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause.Occur;

/**
 * A Query that matches documents matching boolean combinations of other
 * queries, e.g. {@link TermQuery}s, {@link PhraseQuery}s or other
 * BooleanQuerys.
 */
public class BooleanQuery extends Query implements Iterable<BooleanClause> {

	private static int maxClauseCount = 1024;

	/**
	 * Thrown when an attempt is made to add more than {@link
	 * #getMaxClauseCount()} clauses. This typically happens if
	 * a PrefixQuery, FuzzyQuery, WildcardQuery, or TermRangeQuery
	 * is expanded to many terms during search.
	 */
	public static class TooManyClauses extends RuntimeException {
		public TooManyClauses() {
			super("maxClauseCount is set to " + maxClauseCount);
		}
	}

	/**
	 * Return the maximum number of clauses permitted, 1024 by default.
	 * Attempts to add more than the permitted number of clauses cause {@link
	 * TooManyClauses} to be thrown.
	 *
	 * @see #setMaxClauseCount(int)
	 */
	public static int getMaxClauseCount() {
		return maxClauseCount;
	}

	/**
	 * Set the maximum number of clauses permitted per BooleanQuery.
	 * Default value is 1024.
	 */
	public static void setMaxClauseCount(int maxClauseCount) {
		if (maxClauseCount < 1) {
			throw new IllegalArgumentException("maxClauseCount must be >= 1");
		}
		BooleanQuery.maxClauseCount = maxClauseCount;
	}

	/**
	 * A builder for boolean queries.
	 */
	public static class Builder {

		private int minimumNumberShouldMatch;
		private final List<BooleanClause> clauses = new ArrayList<>();

		/**
		 * Sole constructor.
		 */
		public Builder() {
		}

		/**
		 * Specifies a minimum number of the optional BooleanClauses
		 * which must be satisfied.
		 *
		 * <p>
		 * By default no optional clauses are necessary for a match
		 * (unless there are no required clauses).  If this method is used,
		 * then the specified number of clauses is required.
		 * </p>
		 * <p>
		 * Use of this method is totally independent of specifying that
		 * any specific clauses are required (or prohibited).  This number will
		 * only be compared against the number of matching optional clauses.
		 * </p>
		 *
		 * @param min the number of optional clauses that must match
		 */
		public Builder setMinimumNumberShouldMatch(int min) {
			this.minimumNumberShouldMatch = min;
			return this;
		}

		/**
		 * Add a new clause to this {@link Builder}. Note that the order in which
		 * clauses are added does not have any impact on matching documents or query
		 * performance.
		 *
		 * @throws TooManyClauses if the new number of clauses exceeds the maximum clause number
		 */
		public Builder add(BooleanClause clause) {
			if (clauses.size() >= maxClauseCount) {
				throw new TooManyClauses();
			}
			clauses.add(clause);
			return this;
		}

		/**
		 * Add a new clause to this {@link Builder}. Note that the order in which
		 * clauses are added does not have any impact on matching documents or query
		 * performance.
		 *
		 * @throws TooManyClauses if the new number of clauses exceeds the maximum clause number
		 */
		public Builder add(Query query, Occur occur) {
			return add(new BooleanClause(query, occur));
		}

		/**
		 * Create a new {@link BooleanQuery} based on the parameters that have
		 * been set on this builder.
		 */
		public BooleanQuery build() {
			return new BooleanQuery(minimumNumberShouldMatch, clauses.toArray(new BooleanClause[0]));
		}

	}

	private final int minimumNumberShouldMatch;
	private final List<BooleanClause> clauses;              // used for toString() and getClauses()
	private final Map<Occur, Collection<Query>> clauseSets; // used for equals/hashcode

	private BooleanQuery(int minimumNumberShouldMatch,
											 BooleanClause[] clauses) {
		this.minimumNumberShouldMatch = minimumNumberShouldMatch;
		this.clauses = Collections.unmodifiableList(Arrays.asList(clauses));
		clauseSets = new EnumMap<>(Occur.class);
		// duplicates matter for SHOULD and MUST
		clauseSets.put(Occur.SHOULD, new Multiset<>());
		clauseSets.put(Occur.MUST, new Multiset<>());
		// but not for FILTER and MUST_NOT
		clauseSets.put(Occur.FILTER, new HashSet<>());
		clauseSets.put(Occur.MUST_NOT, new HashSet<>());
		for (BooleanClause clause : clauses) {
			clauseSets.get(clause.getOccur()).add(clause.getQuery());
		}
	}

	/**
	 * Gets the minimum number of the optional BooleanClauses
	 * which must be satisfied.
	 */
	public int getMinimumNumberShouldMatch() {
		return minimumNumberShouldMatch;
	}

	/**
	 * Return a list of the clauses of this {@link BooleanQuery}.
	 */
	public List<BooleanClause> clauses() {
		return clauses;
	}

	/**
	 * Return the collection of queries for the given {@link Occur}.
	 */
	Collection<Query> getClauses(Occur occur) {
		return clauseSets.get(occur);
	}

	/**
	 * Returns an iterator on the clauses in this query. It implements the {@link Iterable} interface to
	 * make it possible to do:
	 * <pre class="prettyprint">for (BooleanClause clause : booleanQuery) {}</pre>
	 */
	@Override
	public final Iterator<BooleanClause> iterator() {
		return clauses.iterator();
	}

	private BooleanQuery rewriteNoScoring() {
		if (clauseSets.get(Occur.MUST).size() == 0) {
			return this;
		}
		BooleanQuery.Builder newQuery = new BooleanQuery.Builder();
		newQuery.setMinimumNumberShouldMatch(getMinimumNumberShouldMatch());
		for (BooleanClause clause : clauses) {
			if (clause.getOccur() == Occur.MUST) {
				newQuery.add(clause.getQuery(), Occur.FILTER);
			} else {
				newQuery.add(clause);
			}
		}
		return newQuery.build();
	}

	@Override
	// 构建BooleanQuery的Weight对象树, 不同的Query子类各不相同
	public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
		BooleanQuery query = this;
		if (needsScores == false) {
			query = rewriteNoScoring();
		}
		return new BooleanWeight(query, searcher, needsScores, boost);
	}

	@Override
	public Query rewrite(IndexReader reader) throws IOException {
		if (clauses.size() == 0) {
			return new MatchNoDocsQuery("empty BooleanQuery");
		}

		// optimize 1-clause queries
		if (clauses.size() == 1) { // 逻辑一
			BooleanClause c = clauses.get(0);
			Query query = c.getQuery();
			if (minimumNumberShouldMatch == 1 && c.getOccur() == Occur.SHOULD) {
				return query;
			} else if (minimumNumberShouldMatch == 0) {
				switch (c.getOccur()) {
					case SHOULD:
					case MUST:
						// 直接返回原Query
						return query;
					case FILTER:
						// no scoring clauses, so return a score of 0
						return new BoostQuery(new ConstantScoreQuery(query), 0);
					case MUST_NOT:
						// no positive clauses
						return new MatchNoDocsQuery("pure negative BooleanQuery");
					default:
						throw new AssertionError();
				}
			}
		}

		// recursively rewrite
		// 递归的执行query的重写
		{ // 逻辑二
			// 重新生成一个BooleanQuery的构建器，准备对重写后的Query进行组合。
			BooleanQuery.Builder builder = new BooleanQuery.Builder();
			// 设置一样的MinimumNumberShouldMatch。
			builder.setMinimumNumberShouldMatch(getMinimumNumberShouldMatch());
			boolean actuallyRewritten = false;
			// 遍历每一个query，执行rewrite方法执行重写(具体看各个Query的rewrite，比如TermQuery就没有重写rewriter，所以调用父类Query的方法
			// 直接返回this，说明TermQuery是不需要重写)
			for (BooleanClause clause : this) {
				Query query = clause.getQuery();
				// 调用Query子类的rewrite(...)方法
				// 我们的例子中都是TermQuery，所以直接返回自身this。
				Query rewritten = query.rewrite(reader);
				if (rewritten != query) {
					actuallyRewritten = true;
				}
				builder.add(rewritten, clause.getOccur());
			}
			// 由于我们例子中的的BooleanQuery的Query子类都是TermQuery，不需要重写，所以就不用生成新的BooleanQuery对象
			if (actuallyRewritten) {
				return builder.build();
			}
		}

		// remove duplicate FILTER and MUST_NOT clauses
		{// 逻辑三
			int clauseCount = 0;
			for (Collection<Query> queries : clauseSets.values()) {
				clauseCount += queries.size();
			}
			if (clauseCount != clauses.size()) {
				// since clauseSets implicitly deduplicates FILTER and MUST_NOT
				// clauses, this means there were duplicates
				BooleanQuery.Builder rewritten = new BooleanQuery.Builder();
				rewritten.setMinimumNumberShouldMatch(minimumNumberShouldMatch);
				for (Map.Entry<Occur, Collection<Query>> entry : clauseSets.entrySet()) {
					final Occur occur = entry.getKey();
					for (Query query : entry.getValue()) {
						rewritten.add(query, occur);
					}
				}
				return rewritten.build();
			}
		}

		// Check whether some clauses are both required and excluded
		final Collection<Query> mustNotClauses = clauseSets.get(Occur.MUST_NOT);
		if (!mustNotClauses.isEmpty()) {// 逻辑四
			final Predicate<Query> p = clauseSets.get(Occur.MUST)::contains;
			// 判断是否MUST_NOT跟MUST或FILTER是否有相同的term
			if (mustNotClauses.stream().anyMatch(p.or(clauseSets.get(Occur.FILTER)::contains))) {
				return new MatchNoDocsQuery("FILTER or MUST clause also in MUST_NOT");
			}
			// 判断是否有MatchAllDocsQuery的Query
			if (mustNotClauses.contains(new MatchAllDocsQuery())) {
				return new MatchNoDocsQuery("MUST_NOT clause is MatchAllDocsQuery");
			}
		}

		// remove FILTER clauses that are also MUST clauses
		// or that match all documents
		// 逻辑五
		if (clauseSets.get(Occur.MUST).size() > 0 && clauseSets.get(Occur.FILTER).size() > 0) {
			final Set<Query> filters = new HashSet<Query>(clauseSets.get(Occur.FILTER));
			boolean modified = filters.remove(new MatchAllDocsQuery());
			modified |= filters.removeAll(clauseSets.get(Occur.MUST));
			if (modified) {
				BooleanQuery.Builder builder = new BooleanQuery.Builder();
				builder.setMinimumNumberShouldMatch(getMinimumNumberShouldMatch());
				for (BooleanClause clause : clauses) {
					if (clause.getOccur() != Occur.FILTER) {
						builder.add(clause);
					}
				}
				for (Query filter : filters) {
					builder.add(filter, Occur.FILTER);
				}
				return builder.build();
			}
		}

		// convert FILTER clauses that are also SHOULD clauses to MUST clauses
		if (clauseSets.get(Occur.SHOULD).size() > 0 && clauseSets.get(Occur.FILTER).size() > 0) {// 逻辑六
			final Collection<Query> filters = clauseSets.get(Occur.FILTER);
			final Collection<Query> shoulds = clauseSets.get(Occur.SHOULD);

			Set<Query> intersection = new HashSet<>(filters);
			// 在intersection中保留 FILTER跟SHOUL有相同的term的Query
			intersection.retainAll(shoulds);

			// if语句为真：说明至少有一个term，他即有FILTER又有SHOULD的Query
			if (intersection.isEmpty() == false) {
				// 需要重新生成一个BooleanQuery
				BooleanQuery.Builder builder = new BooleanQuery.Builder();
				int minShouldMatch = getMinimumNumberShouldMatch();

				for (BooleanClause clause : clauses) {
					if (intersection.contains(clause.getQuery())) {
						if (clause.getOccur() == Occur.SHOULD) {
							builder.add(new BooleanClause(clause.getQuery(), Occur.MUST));
							// 对minShouldMatch的值减一，因为这个SHOULD的Query的term，同样是FILTER的term，满足匹配要求的文档必须包含这个term
							minShouldMatch--;
						}
					} else {
						builder.add(clause);
					}
				}
				// 更新minShouldMatch
				builder.setMinimumNumberShouldMatch(Math.max(0, minShouldMatch));
				return builder.build();
			}
		}

		// Deduplicate SHOULD clauses by summing up their boosts
		// 跟逻辑八的几乎一毛一样，这里就不赘述了
		if (clauseSets.get(Occur.SHOULD).size() > 0 && minimumNumberShouldMatch <= 1) {// 逻辑七
			Map<Query, Double> shouldClauses = new HashMap<>();
			for (Query query : clauseSets.get(Occur.SHOULD)) {
				double boost = 1;
				while (query instanceof BoostQuery) {
					BoostQuery bq = (BoostQuery) query;
					boost *= bq.getBoost();
					query = bq.getQuery();
				}
				shouldClauses.put(query, shouldClauses.getOrDefault(query, 0d) + boost);
			}
			if (shouldClauses.size() != clauseSets.get(Occur.SHOULD).size()) {
				BooleanQuery.Builder builder = new BooleanQuery.Builder()
					.setMinimumNumberShouldMatch(minimumNumberShouldMatch);
				for (Map.Entry<Query, Double> entry : shouldClauses.entrySet()) {
					Query query = entry.getKey();
					float boost = entry.getValue().floatValue();
					if (boost != 1f) {
						query = new BoostQuery(query, boost);
					}
					builder.add(query, Occur.SHOULD);
				}
				for (BooleanClause clause : clauses) {
					if (clause.getOccur() != Occur.SHOULD) {
						builder.add(clause);
					}
				}
				return builder.build();
			}
		}

		// Deduplicate MUST clauses by summing up their boosts
		if (clauseSets.get(Occur.MUST).size() > 0) {// 逻辑八
			Map<Query, Double> mustClauses = new HashMap<>();
			// 这里遍历所有的MUST的Clause，如果有重复的Clause，boost值就加1，描述了这个关键字的重要性
			for (Query query : clauseSets.get(Occur.MUST)) {
				double boost = 1;
				while (query instanceof BoostQuery) {
					BoostQuery bq = (BoostQuery) query;
					boost *= bq.getBoost();
					query = bq.getQuery();
				}
				// 调用getOrDefault()查看是否有相同的clause，如果有，那么取出boost，然后对boost进行+1后，覆盖已经存在的clause
				mustClauses.put(query, mustClauses.getOrDefault(query, 0d) + boost);
			}
			// 运行至此，如果BooleanQuery有相同的query，并且是MUST，那么将这些MUST的query合并为一个query，并且增加boost的值
			// if语句为true：说明有重复的clause(MUST), 那么需要对boost不等于1的query重写，然后跟其他的query一起写到新的BooleanQuery中
			if (mustClauses.size() != clauseSets.get(Occur.MUST).size()) {
				BooleanQuery.Builder builder = new BooleanQuery.Builder()
					.setMinimumNumberShouldMatch(minimumNumberShouldMatch);
				// 这个for循环是将那些boost值不等于1的query重写为BoostQuery
				for (Map.Entry<Query, Double> entry : mustClauses.entrySet()) {
					Query query = entry.getKey();
					float boost = entry.getValue().floatValue();
					// if语句为true：那么将query重写为BoostQuery
					if (boost != 1f) {
						query = new BoostQuery(query, boost);
					}
					builder.add(query, Occur.MUST);
				}
				// 把其他不是MUST的clause重写添加到新的BooleanQuery中
				for (BooleanClause clause : clauses) {
					if (clause.getOccur() != Occur.MUST) {
						builder.add(clause);
					}
				}
				return builder.build();
			}
		}

		// Rewrite queries whose single scoring clause is a MUST clause on a
		// MatchAllDocsQuery to a ConstantScoreQuery
		{// 逻辑九
			final Collection<Query> musts = clauseSets.get(Occur.MUST);
			final Collection<Query> filters = clauseSets.get(Occur.FILTER);
			if (musts.size() == 1
				&& filters.size() > 0) {
				Query must = musts.iterator().next();
				float boost = 1f;
				if (must instanceof BoostQuery) {
					BoostQuery boostQuery = (BoostQuery) must;
					must = boostQuery.getQuery();
					boost = boostQuery.getBoost();
				}
				if (must.getClass() == MatchAllDocsQuery.class) {
					// our single scoring clause matches everything: rewrite to a CSQ on the filter
					// ignore SHOULD clause for now
					BooleanQuery.Builder builder = new BooleanQuery.Builder();
					for (BooleanClause clause : clauses) {
						switch (clause.getOccur()) {
							case FILTER:
							case MUST_NOT:
								builder.add(clause);
								break;
							default:
								// ignore
								break;
						}
					}
					Query rewritten = builder.build();
					rewritten = new ConstantScoreQuery(rewritten);
					if (boost != 1f) {
						rewritten = new BoostQuery(rewritten, boost);
					}

					// now add back the SHOULD clauses
					builder = new BooleanQuery.Builder()
						.setMinimumNumberShouldMatch(getMinimumNumberShouldMatch())
						.add(rewritten, Occur.MUST);
					for (Query query : clauseSets.get(Occur.SHOULD)) {
						builder.add(query, Occur.SHOULD);
					}
					rewritten = builder.build();
					return rewritten;
				}
			}
		}
		// 如果都是SHOULD的clause并且minMinimum > 1 那么就不用重写query, 真好
		return super.rewrite(reader);
	}

	/**
	 * Prints a user-readable version of this query.
	 */
	@Override
	public String toString(String field) {
		StringBuilder buffer = new StringBuilder();
		boolean needParens = getMinimumNumberShouldMatch() > 0;
		if (needParens) {
			buffer.append("(");
		}

		int i = 0;
		for (BooleanClause c : this) {
			buffer.append(c.getOccur().toString());

			Query subQuery = c.getQuery();
			if (subQuery instanceof BooleanQuery) {  // wrap sub-bools in parens
				buffer.append("(");
				buffer.append(subQuery.toString(field));
				buffer.append(")");
			} else {
				buffer.append(subQuery.toString(field));
			}

			if (i != clauses.size() - 1) {
				buffer.append(" ");
			}
			i += 1;
		}

		if (needParens) {
			buffer.append(")");
		}

		if (getMinimumNumberShouldMatch() > 0) {
			buffer.append('~');
			buffer.append(getMinimumNumberShouldMatch());
		}

		return buffer.toString();
	}

	/**
	 * Compares the specified object with this boolean query for equality.
	 * Returns true if and only if the provided object<ul>
	 * <li>is also a {@link BooleanQuery},</li>
	 * <li>has the same value of {@link #getMinimumNumberShouldMatch()}</li>
	 * <li>has the same {@link Occur#SHOULD} clauses, regardless of the order</li>
	 * <li>has the same {@link Occur#MUST} clauses, regardless of the order</li>
	 * <li>has the same set of {@link Occur#FILTER} clauses, regardless of the
	 * order and regardless of duplicates</li>
	 * <li>has the same set of {@link Occur#MUST_NOT} clauses, regardless of
	 * the order and regardless of duplicates</li></ul>
	 */
	@Override
	public boolean equals(Object o) {
		return sameClassAs(o) &&
			equalsTo(getClass().cast(o));
	}

	private boolean equalsTo(BooleanQuery other) {
		return getMinimumNumberShouldMatch() == other.getMinimumNumberShouldMatch() &&
			clauseSets.equals(other.clauseSets);
	}

	private int computeHashCode() {
		int hashCode = Objects.hash(minimumNumberShouldMatch, clauseSets);
		if (hashCode == 0) {
			hashCode = 1;
		}
		return hashCode;
	}

	// cached hash code is ok since boolean queries are immutable
	private int hashCode;

	@Override
	public int hashCode() {
		// no need for synchronization, in the worst case we would just compute the hash several times.
		if (hashCode == 0) {
			hashCode = computeHashCode();
			assert hashCode != 0;
		}
		assert hashCode == computeHashCode();
		return hashCode;
	}

}
