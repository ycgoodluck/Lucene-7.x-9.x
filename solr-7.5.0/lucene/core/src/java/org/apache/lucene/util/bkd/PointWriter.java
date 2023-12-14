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
package org.apache.lucene.util.bkd;


import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * Appends many points, and then at the end provides a {@link PointReader} to iterate
 * those points.  This abstracts away whether we write to disk, or use simple arrays
 * in heap.
 *
 * @lucene.internal
 */
public interface PointWriter extends Closeable {
	/**
	 * Add a new point
	 */
	void append(byte[] packedValue, long ord, int docID) throws IOException;

	/**
	 * Returns a {@link PointReader} iterator to step through all previously added points
	 */
	PointReader getReader(long startPoint, long length) throws IOException;

	/**
	 * Returns the single shared reader, used at multiple times during the recursion, to read previously added points
	 */
	PointReader getSharedReader(long startPoint, long length, List<Closeable> toCloseHeroically) throws IOException;

	/**
	 * Removes any temp files behind this writer
	 */
	void destroy() throws IOException;
}

