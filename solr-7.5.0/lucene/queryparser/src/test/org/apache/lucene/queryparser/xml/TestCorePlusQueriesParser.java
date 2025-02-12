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
package org.apache.lucene.queryparser.xml;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;

public class TestCorePlusQueriesParser extends TestCoreParser {

	@Override
	protected CoreParser newCoreParser(String defaultField, Analyzer analyzer) {
		return new CorePlusQueriesParser(defaultField, analyzer);
	}

	public void testLikeThisQueryXML() throws Exception {
		Query q = parse("LikeThisQuery.xml");
		dumpResults("like this", q, 5);
	}

	public void testBoostingQueryXML() throws Exception {
		Query q = parse("BoostingQuery.xml");
		dumpResults("boosting ", q, 5);
	}

}
