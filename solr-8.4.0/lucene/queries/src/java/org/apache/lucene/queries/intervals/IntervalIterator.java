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

package org.apache.lucene.queries.intervals;

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;

/**
 * A {@link DocIdSetIterator} that also allows iteration over matching
 * intervals in a document.
 * <p>
 * Once the iterator is positioned on a document by calling {@link #advance(int)}
 * or {@link #nextDoc()}, intervals may be retrieved by calling {@link #nextInterval()}
 * until {@link #NO_MORE_INTERVALS} is returned.
 * <p>
 * The limits of the current interval are returned by {@link #start()} and {@link #end()}.
 * When the iterator has been moved to a new document, but before {@link #nextInterval()}
 * has been called, both these methods return {@code -1}.
 * <p>
 * Note that it is possible for a document to return {@link #NO_MORE_INTERVALS}
 * on the first call to {@link #nextInterval()}
 */
public abstract class IntervalIterator extends DocIdSetIterator {

	/**
	 * When returned from {@link #nextInterval()}, indicates that there are no more
	 * matching intervals on the current document
	 */
	public static final int NO_MORE_INTERVALS = Integer.MAX_VALUE;

	/**
	 * The start of the current interval
	 * <p>
	 * Returns -1 if {@link #nextInterval()} has not yet been called and {@link #NO_MORE_INTERVALS}
	 * once the iterator is exhausted.
	 */
	public abstract int start();

	/**
	 * The end of the current interval
	 * <p>
	 * Returns -1 if {@link #nextInterval()} has not yet been called and {@link #NO_MORE_INTERVALS}
	 * once the iterator is exhausted.
	 */
	public abstract int end();

	/**
	 * The number of gaps within the current interval
	 * <p>
	 * Note that this returns the number of gaps between the immediate sub-intervals
	 * of this interval, and does not include the gaps inside those sub-intervals.
	 * <p>
	 * Should not be called before {@link #nextInterval()}, or after it has returned
	 * {@link #NO_MORE_INTERVALS}
	 */
	public abstract int gaps();

	/**
	 * Advance the iterator to the next interval
	 *
	 * @return the start of the next interval, or {@link IntervalIterator#NO_MORE_INTERVALS} if
	 * there are no more intervals on the current document
	 */
	public abstract int nextInterval() throws IOException;

	/**
	 * An indication of the average cost of iterating over all intervals in a document
	 *
	 * @see TwoPhaseIterator#matchCost()
	 */
	public abstract float matchCost();

	@Override
	public String toString() {
		return docID() + ":[" + start() + "->" + end() + "]";
	}

}
