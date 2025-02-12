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


import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.SortField;

/**
 * Simple class that binds expression variable names to {@link SortField}s
 * or other {@link Expression}s.
 * <p>
 * Example usage:
 * <pre class="prettyprint">
 *   SimpleBindings bindings = new SimpleBindings();
 *   // document's text relevance score
 *   bindings.add(new SortField("_score", SortField.Type.SCORE));
 *   // integer NumericDocValues field
 *   bindings.add(new SortField("popularity", SortField.Type.INT));
 *   // another expression
 *   bindings.add("recency", myRecencyExpression);
 *
 *   // create a sort field in reverse order
 *   Sort sort = new Sort(expr.getSortField(bindings, true));
 * </pre>
 *
 * @lucene.experimental
 */
public final class SimpleBindings extends Bindings {
	final Map<String, Object> map = new HashMap<>();

	/**
	 * Creates a new empty Bindings
	 */
	public SimpleBindings() {
	}

	/**
	 * Adds a SortField to the bindings.
	 * <p>
	 * This can be used to reference a DocValuesField, a field from
	 * FieldCache, the document's score, etc.
	 */
	public void add(SortField sortField) {
		map.put(sortField.getField(), sortField);
	}

	/**
	 * Bind a {@link DoubleValuesSource} directly to the given name.
	 */
	public void add(String name, DoubleValuesSource source) {
		map.put(name, source);
	}

	/**
	 * Adds an Expression to the bindings.
	 * <p>
	 * This can be used to reference expressions from other expressions.
	 */
	public void add(String name, Expression expression) {
		map.put(name, expression);
	}

	@Override
	public DoubleValuesSource getDoubleValuesSource(String name) {
		Object o = map.get(name);
		if (o == null) {
			throw new IllegalArgumentException("Invalid reference '" + name + "'");
		} else if (o instanceof Expression) {
			return ((Expression) o).getDoubleValuesSource(this);
		} else if (o instanceof DoubleValuesSource) {
			return ((DoubleValuesSource) o);
		}
		SortField field = (SortField) o;
		switch (field.getType()) {
			case INT:
				return DoubleValuesSource.fromIntField(field.getField());
			case LONG:
				return DoubleValuesSource.fromLongField(field.getField());
			case FLOAT:
				return DoubleValuesSource.fromFloatField(field.getField());
			case DOUBLE:
				return DoubleValuesSource.fromDoubleField(field.getField());
			case SCORE:
				return DoubleValuesSource.SCORES;
			default:
				throw new UnsupportedOperationException();
		}
	}

	/**
	 * Traverses the graph of bindings, checking there are no cycles or missing references
	 *
	 * @throws IllegalArgumentException if the bindings is inconsistent
	 */
	public void validate() {
		for (Object o : map.values()) {
			if (o instanceof Expression) {
				Expression expr = (Expression) o;
				try {
					expr.getDoubleValuesSource(this);
				} catch (StackOverflowError e) {
					throw new IllegalArgumentException("Recursion Error: Cycle detected originating in (" + expr.sourceText + ")");
				}
			}
		}
	}
}
