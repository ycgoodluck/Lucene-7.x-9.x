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


import org.apache.lucene.index.TermsEnum; // javadocs
import org.apache.lucene.util.BytesRef;

/**
 * Contains statistics for a specific term
 *
 * @lucene.experimental
 */
// TermStatistics类用来描述 一个域值的一些信息
public class TermStatistics {
	private final BytesRef term;
	private final long docFreq;
	private final long totalTermFreq;

	public TermStatistics(BytesRef term, long docFreq, long totalTermFreq) {
		assert docFreq >= 0;
		assert totalTermFreq == -1 || totalTermFreq >= docFreq; // #positions must be >= #postings
		this.term = term;
		this.docFreq = docFreq;
		this.totalTermFreq = totalTermFreq;
	}

	/**
	 * returns the term text
	 */
	// 域值
	public final BytesRef term() {
		return term;
	}

	/**
	 * returns the number of documents this term occurs in
	 *
	 * @see TermsEnum#docFreq()
	 */
	// 包含域值的文档个数
	public final long docFreq() {
		return docFreq;
	}

	/**
	 * returns the total number of occurrences of this term
	 *
	 * @see TermsEnum#totalTermFreq()
	 */
	// 域值在所有文档中出现的总数（不是去重的）
	public final long totalTermFreq() {
		return totalTermFreq;
	}
}
