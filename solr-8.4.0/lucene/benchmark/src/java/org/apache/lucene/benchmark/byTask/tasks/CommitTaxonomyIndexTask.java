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
package org.apache.lucene.benchmark.byTask.tasks;

import org.apache.lucene.benchmark.byTask.PerfRunData;
import org.apache.lucene.facet.taxonomy.TaxonomyWriter;

/**
 * Commits the Taxonomy Index.
 */
public class CommitTaxonomyIndexTask extends PerfTask {
	public CommitTaxonomyIndexTask(PerfRunData runData) {
		super(runData);
	}

	@Override
	public int doLogic() throws Exception {
		TaxonomyWriter taxonomyWriter = getRunData().getTaxonomyWriter();
		if (taxonomyWriter != null) {
			taxonomyWriter.commit();
		} else {
			throw new IllegalStateException("TaxonomyWriter is not currently open");
		}

		return 1;
	}
}
