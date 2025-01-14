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
package org.apache.lucene.expressions;

import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.Rescorer;
import org.apache.lucene.search.SortField;

/**
 * Base class that computes the value of an expression for a document.
 * <p>
 * Example that sorts based on an expression:
 * <pre class="prettyprint">
 *   // compile an expression:
 *   Expression expr = JavascriptCompiler.compile("sqrt(_score) + ln(popularity)");
 *
 *   // SimpleBindings just maps variables to SortField instances
 *   SimpleBindings bindings = new SimpleBindings();
 *   bindings.add(new SortField("_score", SortField.Type.SCORE));
 *   bindings.add(new SortField("popularity", SortField.Type.INT));
 *
 *   // create a sort field and sort by it (reverse order)
 *   Sort sort = new Sort(expr.getSortField(bindings, true));
 *   Query query = new TermQuery(new Term("body", "contents"));
 *   searcher.search(query, 10, sort);
 * </pre>
 * <p>
 * Example that modifies the scores produced by the query:
 * <pre class="prettyprint">
 *   // compile an expression:
 *   Expression expr = JavascriptCompiler.compile("sqrt(_score) + ln(popularity)");
 *
 *   // SimpleBindings just maps variables to SortField instances
 *   SimpleBindings bindings = new SimpleBindings();
 *   bindings.add(new SortField("_score", SortField.Type.SCORE));
 *   bindings.add(new SortField("popularity", SortField.Type.INT));
 *
 *   // create a query that matches based on body:contents but
 *   // scores using expr
 *   Query query = new FunctionScoreQuery(
 *       new TermQuery(new Term("body", "contents")),
 *       expr.getDoubleValuesSource(bindings));
 *   searcher.search(query, 10);
 * </pre>
 *
 * @lucene.experimental
 * @see JavascriptCompiler#compile
 */
public abstract class Expression {

	/**
	 * The original source text
	 */
	public final String sourceText;

	/**
	 * Named variables referred to by this expression
	 */
	public final String[] variables;

	/**
	 * Creates a new {@code Expression}.
	 *
	 * @param sourceText Source text for the expression: e.g. {@code ln(popularity)}
	 * @param variables  Names of external variables referred to by the expression
	 */
	protected Expression(String sourceText, String[] variables) {
		this.sourceText = sourceText;
		this.variables = variables;
	}

	/**
	 * Evaluates the expression for the current document.
	 *
	 * @param functionValues {@link DoubleValues} for each element of {@link #variables}.
	 * @return The computed value of the expression for the given document.
	 */
	public abstract double evaluate(DoubleValues[] functionValues);

	/**
	 * Get a DoubleValuesSource which can compute the value of this expression in the context of the given bindings.
	 *
	 * @param bindings Bindings to use for external values in this expression
	 * @return A DoubleValuesSource which will evaluate this expression when used
	 */
	public DoubleValuesSource getDoubleValuesSource(Bindings bindings) {
		return new ExpressionValueSource(bindings, this);
	}

	/**
	 * Get a sort field which can be used to rank documents by this expression.
	 */
	public SortField getSortField(Bindings bindings, boolean reverse) {
		return getDoubleValuesSource(bindings).getSortField(reverse);
	}

	/**
	 * Get a {@link Rescorer}, to rescore first-pass hits
	 * using this expression.
	 */
	public Rescorer getRescorer(Bindings bindings) {
		return new ExpressionRescorer(this, bindings);
	}
}
