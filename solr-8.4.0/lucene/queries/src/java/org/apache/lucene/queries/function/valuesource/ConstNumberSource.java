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
package org.apache.lucene.queries.function.valuesource;

import org.apache.lucene.queries.function.ValueSource;

/**
 * <code>ConstNumberSource</code> is the base class for all constant numbers
 */
public abstract class ConstNumberSource extends ValueSource {
	public abstract int getInt();

	public abstract long getLong();

	public abstract float getFloat();

	public abstract double getDouble();

	public abstract Number getNumber();

	public abstract boolean getBool();
}
