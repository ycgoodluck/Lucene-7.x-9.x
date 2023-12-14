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
package org.apache.lucene.queryparser.flexible.standard.processors;

import org.apache.lucene.queryparser.flexible.core.config.QueryConfigHandler;
import org.apache.lucene.queryparser.flexible.core.processors.NoChildOptimizationQueryNodeProcessor;
import org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessorPipeline;
import org.apache.lucene.queryparser.flexible.core.processors.RemoveDeletedQueryNodesProcessor;
import org.apache.lucene.queryparser.flexible.standard.builders.StandardQueryTreeBuilder;
import org.apache.lucene.queryparser.flexible.standard.config.StandardQueryConfigHandler;
import org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParser;
import org.apache.lucene.search.Query;

/**
 * This pipeline has all the processors needed to process a query node tree,
 * generated by {@link StandardSyntaxParser}, already assembled. <br>
 * <br>
 * The order they are assembled affects the results. <br>
 * <br>
 * This processor pipeline was designed to work with
 * {@link StandardQueryConfigHandler}. <br>
 * <br>
 * The result query node tree can be used to build a {@link Query} object using
 * {@link StandardQueryTreeBuilder}.
 *
 * @see StandardQueryTreeBuilder
 * @see StandardQueryConfigHandler
 * @see StandardSyntaxParser
 */
public class StandardQueryNodeProcessorPipeline extends
	QueryNodeProcessorPipeline {

	public StandardQueryNodeProcessorPipeline(QueryConfigHandler queryConfig) {
		super(queryConfig);

		add(new WildcardQueryNodeProcessor());
		add(new MultiFieldQueryNodeProcessor());
		add(new FuzzyQueryNodeProcessor());
		add(new RegexpQueryNodeProcessor());
		add(new MatchAllDocsQueryNodeProcessor());
		add(new OpenRangeQueryNodeProcessor());
		add(new PointQueryNodeProcessor());
		add(new PointRangeQueryNodeProcessor());
		add(new TermRangeQueryNodeProcessor());
		add(new AllowLeadingWildcardProcessor());
		add(new AnalyzerQueryNodeProcessor());
		add(new PhraseSlopQueryNodeProcessor());
		//add(new GroupQueryNodeProcessor());
		add(new BooleanQuery2ModifierNodeProcessor());
		add(new NoChildOptimizationQueryNodeProcessor());
		add(new RemoveDeletedQueryNodesProcessor());
		add(new RemoveEmptyNonLeafQueryNodeProcessor());
		add(new BooleanSingleChildOptimizationQueryNodeProcessor());
		add(new DefaultPhraseSlopQueryNodeProcessor());
		add(new BoostQueryNodeProcessor());
		add(new MultiTermRewriteMethodProcessor());
	}

}
