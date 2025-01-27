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

package org.apache.lucene.search.grouping;

import java.io.IOException;
import java.util.Collection;

import org.apache.lucene.index.LeafReaderContext;

/**
 * Defines a group, for use by grouping collectors
 * <p>
 * A GroupSelector acts as an iterator over documents.  For each segment, clients
 * should call {@link #setNextReader(LeafReaderContext)}, and then {@link #advanceTo(int)}
 * for each matching document.
 *
 * @param <T> the type of the group value
 */
public abstract class GroupSelector<T> {

	/**
	 * What to do with the current value
	 */
	public enum State {SKIP, ACCEPT}

	/**
	 * Set the LeafReaderContext
	 */
	public abstract void setNextReader(LeafReaderContext readerContext) throws IOException;

	/**
	 * Advance the GroupSelector's iterator to the given document
	 */
	public abstract State advanceTo(int doc) throws IOException;

	/**
	 * Get the group value of the current document
	 * <p>
	 * N.B. this object may be reused, for a persistent version use {@link #copyValue()}
	 */
	public abstract T currentValue();

	/**
	 * @return a copy of the group value of the current document
	 */
	public abstract T copyValue();

	/**
	 * Set a restriction on the group values returned by this selector
	 * <p>
	 * If the selector is positioned on a document whose group value is not contained
	 * within this set, then {@link #advanceTo(int)} will return {@link State#SKIP}
	 *
	 * @param groups a set of {@link SearchGroup} objects to limit selections to
	 */
	public abstract void setGroups(Collection<SearchGroup<T>> groups);

}
