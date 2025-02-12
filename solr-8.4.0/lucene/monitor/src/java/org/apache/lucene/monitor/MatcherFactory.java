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

package org.apache.lucene.monitor;

import org.apache.lucene.search.IndexSearcher;

/**
 * Interface for the creation of new CandidateMatcher objects
 *
 * @param <T> a subclass of {@link CandidateMatcher}
 */
public interface MatcherFactory<T extends QueryMatch> {

	/**
	 * Create a new {@link CandidateMatcher} object, to select
	 * queries to match against the passed-in IndexSearcher
	 */
	CandidateMatcher<T> createMatcher(IndexSearcher searcher);

}
