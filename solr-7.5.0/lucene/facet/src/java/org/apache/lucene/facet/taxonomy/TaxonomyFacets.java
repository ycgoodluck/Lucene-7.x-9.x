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
package org.apache.lucene.facet.taxonomy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsConfig.DimConfig; // javadocs
import org.apache.lucene.facet.FacetsConfig;

/**
 * Base class for all taxonomy-based facets impls.
 */
public abstract class TaxonomyFacets extends Facets {

	private static final Comparator<FacetResult> BY_VALUE_THEN_DIM = new Comparator<FacetResult>() {
		@Override
		public int compare(FacetResult a, FacetResult b) {
			if (a.value.doubleValue() > b.value.doubleValue()) {
				return -1;
			} else if (b.value.doubleValue() > a.value.doubleValue()) {
				return 1;
			} else {
				return a.dim.compareTo(b.dim);
			}
		}
	};

	/**
	 * Index field name provided to the constructor.
	 */
	protected final String indexFieldName;

	/**
	 * {@code TaxonomyReader} provided to the constructor.
	 */
	protected final TaxonomyReader taxoReader;

	/**
	 * {@code FacetsConfig} provided to the constructor.
	 */
	protected final FacetsConfig config;

	/**
	 * Maps parent ordinal to its child, or -1 if the parent
	 * is childless.
	 */
	private int[] children;

	/**
	 * Maps an ordinal to its sibling, or -1 if there is no
	 * sibling.
	 */
	private int[] siblings;

	/**
	 * Maps an ordinal to its parent, or -1 if there is no
	 * parent (root node).
	 */
	protected final int[] parents;

	/**
	 * Sole constructor.
	 */
	protected TaxonomyFacets(String indexFieldName, TaxonomyReader taxoReader, FacetsConfig config) throws IOException {
		this.indexFieldName = indexFieldName;
		this.taxoReader = taxoReader;
		this.config = config;
		parents = taxoReader.getParallelTaxonomyArrays().parents();
	}

	/**
	 * Returns int[] mapping each ordinal to its first child; this is a large array and
	 * is computed (and then saved) the first time this method is invoked.
	 */
	protected int[] getChildren() throws IOException {
		if (children == null) {
			children = taxoReader.getParallelTaxonomyArrays().children();
		}
		return children;
	}

	/**
	 * Returns int[] mapping each ordinal to its next sibling; this is a large array and
	 * is computed (and then saved) the first time this method is invoked.
	 */
	protected int[] getSiblings() throws IOException {
		if (siblings == null) {
			siblings = taxoReader.getParallelTaxonomyArrays().siblings();
		}
		return siblings;
	}

	/**
	 * Returns true if the (costly, and lazily initialized) children int[] was initialized.
	 *
	 * @lucene.experimental
	 */
	public boolean childrenLoaded() {
		return children != null;
	}

	/**
	 * Returns true if the (costly, and lazily initialized) sibling int[] was initialized.
	 *
	 * @lucene.experimental
	 */
	public boolean siblingsLoaded() {
		return children != null;
	}

	/**
	 * Throws {@code IllegalArgumentException} if the
	 * dimension is not recognized.  Otherwise, returns the
	 * {@link DimConfig} for this dimension.
	 */
	protected FacetsConfig.DimConfig verifyDim(String dim) {
		FacetsConfig.DimConfig dimConfig = config.getDimConfig(dim);
		if (!dimConfig.indexFieldName.equals(indexFieldName)) {
			throw new IllegalArgumentException("dimension \"" + dim + "\" was not indexed into field \"" + indexFieldName + "\"");
		}
		return dimConfig;
	}


	@Override
	// 调用这个方法获得结果的过程中，如果label的值是多值，并没有什么意义，他只是统计了dim的子ordinal的信息
	public List<FacetResult> getAllDims(int topN) throws IOException {
		int[] children = getChildren();
		int[] siblings = getSiblings();
		int ord = children[TaxonomyReader.ROOT_ORDINAL];
		List<FacetResult> results = new ArrayList<>();
		// 统计所有的dim的数据, 这些dim也有相同的父ordinal值
		while (ord != TaxonomyReader.INVALID_ORDINAL) {
			String dim = taxoReader.getPath(ord).components[0];
			FacetsConfig.DimConfig dimConfig = config.getDimConfig(dim);
			if (dimConfig.indexFieldName.equals(indexFieldName)) {
				FacetResult result = getTopChildren(topN, dim);
				if (result != null) {
					results.add(result);
				}
			}
			ord = siblings[ord];
		}

		// Sort by highest value, tie break by dim:
		// 根据value(包含这个ordinal的文档个数(去重))进行排序
		Collections.sort(results, BY_VALUE_THEN_DIM);
		return results;
	}
}
