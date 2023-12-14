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
package org.apache.lucene.codecs.lucene50;


import static org.apache.lucene.codecs.lucene50.ForUtil.MAX_DATA_SIZE;
import static org.apache.lucene.codecs.lucene50.ForUtil.MAX_ENCODED_SIZE;
import static org.apache.lucene.codecs.lucene50.Lucene50PostingsFormat.BLOCK_SIZE;
import static org.apache.lucene.codecs.lucene50.Lucene50PostingsFormat.DOC_CODEC;
import static org.apache.lucene.codecs.lucene50.Lucene50PostingsFormat.MAX_SKIP_LEVELS;
import static org.apache.lucene.codecs.lucene50.Lucene50PostingsFormat.PAY_CODEC;
import static org.apache.lucene.codecs.lucene50.Lucene50PostingsFormat.POS_CODEC;
import static org.apache.lucene.codecs.lucene50.Lucene50PostingsFormat.TERMS_CODEC;
import static org.apache.lucene.codecs.lucene50.Lucene50PostingsFormat.VERSION_CURRENT;

import java.io.IOException;

import org.apache.lucene.codecs.BlockTermState;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.PushPostingsWriterBase;
import org.apache.lucene.codecs.lucene50.Lucene50PostingsFormat.IntBlockTermState;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.packed.PackedInts;

/**
 * Concrete class that writes docId(maybe frq,pos,offset,payloads) list
 * with postings format.
 * <p>
 * Postings list for each term will be stored separately.
 *
 * @lucene.experimental
 * @see Lucene50SkipWriter for details about skipping setting and postings layout.
 */
public final class Lucene50PostingsWriter extends PushPostingsWriterBase {

	IndexOutput docOut;
	IndexOutput posOut;
	IndexOutput payOut;

	final static IntBlockTermState emptyState = new IntBlockTermState();
	IntBlockTermState lastState;

	// Holds starting file pointers for current term:
	// .doc中的起始位置
	private long docStartFP;
	// .pos文件中的位置
	private long posStartFP;
	private long payStartFP;

	final int[] docDeltaBuffer;
	final int[] freqBuffer;
	private int docBufferUpto;

	final int[] posDeltaBuffer;
	final int[] payloadLengthBuffer;
	final int[] offsetStartDeltaBuffer;
	final int[] offsetLengthBuffer;
	private int posBufferUpto;

	private byte[] payloadBytes;
	private int payloadByteUpto;

	private int lastBlockDocID;
	private long lastBlockPosFP;
	private long lastBlockPayFP;
	private int lastBlockPosBufferUpto;
	private int lastBlockPayloadByteUpto;

	private int lastDocID;
	private int lastPosition;
	private int lastStartOffset;
	private int docCount;

	final byte[] encoded;

	private final ForUtil forUtil;
	private final Lucene50SkipWriter skipWriter;

	/**
	 * Creates a postings writer
	 */
	public Lucene50PostingsWriter(SegmentWriteState state) throws IOException {
		final float acceptableOverheadRatio = PackedInts.COMPACT;

		String docFileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, Lucene50PostingsFormat.DOC_EXTENSION);
		docOut = state.directory.createOutput(docFileName, state.context);
		IndexOutput posOut = null;
		IndexOutput payOut = null;
		boolean success = false;
		try {
			CodecUtil.writeIndexHeader(docOut, DOC_CODEC, VERSION_CURRENT,
				state.segmentInfo.getId(), state.segmentSuffix);
			forUtil = new ForUtil(acceptableOverheadRatio, docOut);
			if (state.fieldInfos.hasProx()) {
				posDeltaBuffer = new int[MAX_DATA_SIZE];
				String posFileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, Lucene50PostingsFormat.POS_EXTENSION);
				posOut = state.directory.createOutput(posFileName, state.context);
				CodecUtil.writeIndexHeader(posOut, POS_CODEC, VERSION_CURRENT,
					state.segmentInfo.getId(), state.segmentSuffix);

				if (state.fieldInfos.hasPayloads()) {
					payloadBytes = new byte[128];
					payloadLengthBuffer = new int[MAX_DATA_SIZE];
				} else {
					payloadBytes = null;
					payloadLengthBuffer = null;
				}

				if (state.fieldInfos.hasOffsets()) {
					offsetStartDeltaBuffer = new int[MAX_DATA_SIZE];
					offsetLengthBuffer = new int[MAX_DATA_SIZE];
				} else {
					offsetStartDeltaBuffer = null;
					offsetLengthBuffer = null;
				}

				if (state.fieldInfos.hasPayloads() || state.fieldInfos.hasOffsets()) {
					String payFileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, Lucene50PostingsFormat.PAY_EXTENSION);
					payOut = state.directory.createOutput(payFileName, state.context);
					CodecUtil.writeIndexHeader(payOut, PAY_CODEC, VERSION_CURRENT,
						state.segmentInfo.getId(), state.segmentSuffix);
				}
			} else {
				posDeltaBuffer = null;
				payloadLengthBuffer = null;
				offsetStartDeltaBuffer = null;
				offsetLengthBuffer = null;
				payloadBytes = null;
			}
			this.payOut = payOut;
			this.posOut = posOut;
			success = true;
		} finally {
			if (!success) {
				IOUtils.closeWhileHandlingException(docOut, posOut, payOut);
			}
		}

		docDeltaBuffer = new int[MAX_DATA_SIZE];
		freqBuffer = new int[MAX_DATA_SIZE];

		// TODO: should we try skipping every 2/4 blocks...?
		skipWriter = new Lucene50SkipWriter(MAX_SKIP_LEVELS,
			BLOCK_SIZE,
			state.segmentInfo.maxDoc(),
			docOut,
			posOut,
			payOut);

		encoded = new byte[MAX_ENCODED_SIZE];
	}

	@Override
	public IntBlockTermState newTermState() {
		return new IntBlockTermState();
	}

	@Override
	public void init(IndexOutput termsOut, SegmentWriteState state) throws IOException {
		CodecUtil.writeIndexHeader(termsOut, TERMS_CODEC, VERSION_CURRENT, state.segmentInfo.getId(), state.segmentSuffix);
		termsOut.writeVInt(BLOCK_SIZE);
	}

	@Override
	public int setField(FieldInfo fieldInfo) {
		super.setField(fieldInfo);
		skipWriter.setField(writePositions, writeOffsets, writePayloads);
		lastState = emptyState;
		if (writePositions) {
			if (writePayloads || writeOffsets) {
				return 3;  // doc + pos + pay FP
			} else {
				return 2;  // doc + pos FP
			}
		} else {
			return 1;    // doc FP
		}
	}

	@Override
	public void startTerm() {
		// 获取.doc文件可以写入的位置
		docStartFP = docOut.getFilePointer();
		if (writePositions) {
			// 获取.pos文件可以写入的位置
			posStartFP = posOut.getFilePointer();
			if (writePayloads || writeOffsets) {
				payStartFP = payOut.getFilePointer();
			}
		}
		// 初始化操作
		lastDocID = 0;
		lastBlockDocID = -1;
		skipWriter.resetSkip();
	}

	@Override
	public void startDoc(int docID, int termDocFreq) throws IOException {
		// Have collected a block of docs, and get a new doc.
		// Should write skip data as well as postings list for
		// current block.
		// 每处理128篇文档，docBufferUpto的值就会在finishDoc()方法中被置为0.
		// 每处理128篇文档, lastBlockDocID的值就会在finishDoc()为置为 上一个文档号
		if (lastBlockDocID != -1 && docBufferUpto == 0) {
			// docCount表示已经处理多少篇包含当前term的文档
			// lastBlockPosFP表示在 .pos文件中的一个位置，这个位置前的一个block数据是term在128篇文档中的位置信息
			skipWriter.bufferSkip(lastBlockDocID, docCount, lastBlockPosFP, lastBlockPayFP, lastBlockPosBufferUpto, lastBlockPayloadByteUpto);
		}

		// 计算文档号差值
		final int docDelta = docID - lastDocID;

		if (docID < 0 || (docCount > 0 && docDelta <= 0)) {
			throw new CorruptIndexException("docs out of order (" + docID + " <= " + lastDocID + " )", docOut);
		}

		// docDeltaBuffer[]使用差值存储文档号
		docDeltaBuffer[docBufferUpto] = docDelta;
		if (writeFreqs) {
			// freqBuffer[]存储当前term在每一篇文档的词频
			freqBuffer[docBufferUpto] = termDocFreq;
		}

		// 每次处理128篇文档，docBufferUpto的值会在 finishDoc()方法中被重置为0.
		docBufferUpto++;
		// docCount用来统计目前已经处理的文档个数
		docCount++;

		if (docBufferUpto == BLOCK_SIZE) {
			forUtil.writeBlock(docDeltaBuffer, encoded, docOut);
			if (writeFreqs) {
				forUtil.writeBlock(freqBuffer, encoded, docOut);
			}
			// NOTE: don't set docBufferUpto back to 0 here;
			// finishDoc will do so (because it needs to see that
			// the block was filled so it can save skip data)
		}


		lastDocID = docID;
		lastPosition = 0;
		lastStartOffset = 0;
	}

	@Override
	public void addPosition(int position, BytesRef payload, int startOffset, int endOffset) throws IOException {
		if (position > IndexWriter.MAX_POSITION) {
			throw new CorruptIndexException("position=" + position + " is too large (> IndexWriter.MAX_POSITION=" + IndexWriter.MAX_POSITION + ")", docOut);
		}
		if (position < 0) {
			throw new CorruptIndexException("position=" + position + " is < 0", docOut);
		}
		// 差值存储position, posDeltaBuffer[]数组中存放了当前term在每一篇文档中的位置信息，其中同一篇文档中的位置用差值存储
		posDeltaBuffer[posBufferUpto] = position - lastPosition;
		if (writePayloads) {
			if (payload == null || payload.length == 0) {
				// no payload 当前位置没有payload
				payloadLengthBuffer[posBufferUpto] = 0;
			} else {
				// 记录payload的长度
				payloadLengthBuffer[posBufferUpto] = payload.length;
				if (payloadByteUpto + payload.length > payloadBytes.length) {
					payloadBytes = ArrayUtil.grow(payloadBytes, payloadByteUpto + payload.length);
				}
				// 将payload数据写入到payloadBytes数组中
				System.arraycopy(payload.bytes, payload.offset, payloadBytes, payloadByteUpto, payload.length);
				payloadByteUpto += payload.length;
			}
		}

		if (writeOffsets) {
			assert startOffset >= lastStartOffset;
			assert endOffset >= startOffset;
			// 差值存储startOffset
			offsetStartDeltaBuffer[posBufferUpto] = startOffset - lastStartOffset;
			// 存储其实就是term的长度
			offsetLengthBuffer[posBufferUpto] = endOffset - startOffset;
			lastStartOffset = startOffset;
		}
		// 当posBufferUpto达到BLOCK_SIZE即128，posDeltaBuffer[]数组中的数据会被写入到.pos文件中，并且posBufferUpto为置为0
		// 使得posDeltaBuffer[]数组可以复用
		// posBufferUpto另外用来描述一个term在payloadLengthBuffer数组、offsetStartDeltaBuffer数组、offsetLengthBuffer、posDeltaBuffer[]数组中的position、payload、offset数据
		posBufferUpto++;
		// 保存当前position的值，如果term在当前文档中还有新位置信息，那么下次处理时position的值用于计算差值
		lastPosition = position;
		if (posBufferUpto == BLOCK_SIZE) {
			forUtil.writeBlock(posDeltaBuffer, encoded, posOut);

			if (writePayloads) {
				forUtil.writeBlock(payloadLengthBuffer, encoded, payOut);
				payOut.writeVInt(payloadByteUpto);
				payOut.writeBytes(payloadBytes, 0, payloadByteUpto);
				payloadByteUpto = 0;
			}
			if (writeOffsets) {
				forUtil.writeBlock(offsetStartDeltaBuffer, encoded, payOut);
				forUtil.writeBlock(offsetLengthBuffer, encoded, payOut);
			}
			posBufferUpto = 0;
		}
	}

	@Override
	public void finishDoc() throws IOException {
		// Since we don't know df for current term, we had to buffer
		// those skip data for each block, and when a new doc comes,
		// write them to skip file.
		// df（document frequency）指的是包含term的文档数
		// 每次处理包含当前term的128篇文档后，会将这个term在这128篇文档中的倒排表进行快处理
		if (docBufferUpto == BLOCK_SIZE) {
			lastBlockDocID = lastDocID;
			if (posOut != null) {
				if (payOut != null) {
					lastBlockPayFP = payOut.getFilePointer();
				}
				// 记录.pos中的位置，该位置的一个block数据描述了term在128文档中的所有位置信息
				lastBlockPosFP = posOut.getFilePointer();
				lastBlockPosBufferUpto = posBufferUpto;
				lastBlockPayloadByteUpto = payloadByteUpto;
			}
			// 注意这里的docBufferUpto被置为0，意味着docDeltaBuffer[]数组被复用
			docBufferUpto = 0;
		}
	}

	/**
	 * Called when we are done adding docs to this term
	 */
	@Override
	public void finishTerm(BlockTermState _state) throws IOException {
		IntBlockTermState state = (IntBlockTermState) _state;
		assert state.docFreq > 0;

		// TODO: wasteful we are counting this (counting # docs
		// for this term) in two places?
		assert state.docFreq == docCount : state.docFreq + " vs " + docCount;

		// docFreq == 1, don't write the single docid/freq to a separate file along with a pointer to it.
		final int singletonDocID;
		if (state.docFreq == 1) {
			// pulse the singleton docid into the term dictionary, freq is implicitly totalTermFreq
			singletonDocID = docDeltaBuffer[0];
		} else {
			singletonDocID = -1;
			// vInt encode the remaining doc deltas and freqs:
			// 每当处理某个term的128篇文档会生成一块，最后一个block中剩余docBufferUpto文档还没有处理
			for (int i = 0; i < docBufferUpto; i++) {
				final int docDelta = docDeltaBuffer[i];
				final int freq = freqBuffer[i];
				if (!writeFreqs) {
					docOut.writeVInt(docDelta);
				} else if (freqBuffer[i] == 1) {
					docOut.writeVInt((docDelta << 1) | 1);
				} else {
					docOut.writeVInt(docDelta << 1);
					docOut.writeVInt(freq);
				}
			}
		}

		final long lastPosBlockOffset;

		if (writePositions) {
			// totalTermFreq is just total number of positions(or payloads, or offsets)
			// associated with current term.
			assert state.totalTermFreq != -1;
			if (state.totalTermFreq > BLOCK_SIZE) {
				// record file offset for last pos in last block
				lastPosBlockOffset = posOut.getFilePointer() - posStartFP;
			} else {
				lastPosBlockOffset = -1;
			}
			if (posBufferUpto > 0) {
				// TODO: should we send offsets/payloads to
				// .pay...?  seems wasteful (have to store extra
				// vLong for low (< BLOCK_SIZE) DF terms = vast vast
				// majority)

				// vInt encode the remaining positions/payloads/offsets:
				int lastPayloadLength = -1;  // force first payload length to be written
				int lastOffsetLength = -1;   // force first offset length to be written
				int payloadBytesReadUpto = 0;
				// 处理剩余的posBufferUpto个的当前term的位置信息。posBufferUpto的值必定是小于BLOCK_SIZE
				for (int i = 0; i < posBufferUpto; i++) {
					final int posDelta = posDeltaBuffer[i];
					if (writePayloads) {
						final int payloadLength = payloadLengthBuffer[i];
						if (payloadLength != lastPayloadLength) {
							lastPayloadLength = payloadLength;
							posOut.writeVInt((posDelta << 1) | 1);
							posOut.writeVInt(payloadLength);
						} else {
							posOut.writeVInt(posDelta << 1);
						}

						if (payloadLength != 0) {
							posOut.writeBytes(payloadBytes, payloadBytesReadUpto, payloadLength);
							payloadBytesReadUpto += payloadLength;
						}
					} else {
						// 剩余的不满128个的位置信息没有使用PackInt来进行压缩存储
						posOut.writeVInt(posDelta);
					}

					if (writeOffsets) {
						int delta = offsetStartDeltaBuffer[i];
						int length = offsetLengthBuffer[i];
						if (length == lastOffsetLength) {
							posOut.writeVInt(delta << 1);
						} else {
							posOut.writeVInt(delta << 1 | 1);
							posOut.writeVInt(length);
							lastOffsetLength = length;
						}
					}
				}

				if (writePayloads) {
					assert payloadBytesReadUpto == payloadByteUpto;
					payloadByteUpto = 0;
				}
			}
		} else {
			lastPosBlockOffset = -1;
		}

		long skipOffset;
		if (docCount > BLOCK_SIZE) {
			skipOffset = skipWriter.writeSkip(docOut) - docStartFP;
		} else {
			skipOffset = -1;
		}

		// 记录当前term的信息在 .doc文件中的起始位置，在每次处理term前调用 startTerm()来初始化这个值
		state.docStartFP = docStartFP;
		// 记录当前term的信息在 .pos文件中的起始位置，在每次处理term前调用 startTerm()来初始化这个值
		state.posStartFP = posStartFP;
		// 记录当前term的信息在 .pay文件中的起始位置，在每次处理term前调用 startTerm()来初始化这个值
		state.payStartFP = payStartFP;
		// 如果包含term的文档号个数只有一个的情况
		state.singletonDocID = singletonDocID;
		// skipOffset用来描述term在.doc文件中 跳表信息的起始位置，不过skipOffset是差值存储，所以跳表信息的真实其实地址是 (skipOffset + docStartFP)
		state.skipOffset = skipOffset;
		// 如果term的词频大于BLOC_SIZE,即大于128个，那么在.pos文件中就会生成一个block，lastPosBlockOffset记录最后一个block结束位置
		// 通过这个位置就能快速定位到term的剩余的position信息，由于这些position信息的个数肯定是不满128个，可以看Lucene50PostingsWriter.java中finishTerm()的方法
		state.lastPosBlockOffset = lastPosBlockOffset;
		docBufferUpto = 0;
		posBufferUpto = 0;
		lastDocID = 0;
		docCount = 0;
	}

	@Override
	public void encodeTerm(long[] longs, DataOutput out, FieldInfo fieldInfo, BlockTermState _state, boolean absolute) throws IOException {
		IntBlockTermState state = (IntBlockTermState) _state;
		// absolute为真，说明是差值存储
		if (absolute) {
			lastState = emptyState;
		}
		// docStartFP是term在.doc文件中的起始位置, 差值存储
		longs[0] = state.docStartFP - lastState.docStartFP;
		if (writePositions) {
			// posStartFP是term在.pos文件中的起始位置, 差值存储
			longs[1] = state.posStartFP - lastState.posStartFP;
			if (writePayloads || writeOffsets) {
				// payStartFP是term在.pay文件中的起始位置, 差值存储
				longs[2] = state.payStartFP - lastState.payStartFP;
			}
		}
		// 如果包含term的文档号个数只有一个的情况
		if (state.singletonDocID != -1) {
			out.writeVInt(state.singletonDocID);
		}
		if (writePositions) {
			if (state.lastPosBlockOffset != -1) {
				// 如果term的词频大于BLOC_SIZE,即大于128个，那么在.pos文件中就会生成一个block，lastPosBlockOffset记录最后一个block结束位置
				// 通过这个位置就能快速定位到term的剩余的position信息，并且这些position信息的个数肯定是不满128个，可以看Lucene50PostingsWriter.java中finishTerm()的方法
				out.writeVLong(state.lastPosBlockOffset);
			}
		}
		// skipOffset用来描述term在.doc文件中 跳表信息的起始位置，不过skipOffset是差值存储，所以跳表信息的真实其实地址是 (skipOffset + docStartFP)
		if (state.skipOffset != -1) {
			out.writeVLong(state.skipOffset);
		}
		lastState = state;
	}

	@Override
	public void close() throws IOException {
		// TODO: add a finish() at least to PushBase? DV too...?
		boolean success = false;
		try {
			if (docOut != null) {
				CodecUtil.writeFooter(docOut);
			}
			if (posOut != null) {
				CodecUtil.writeFooter(posOut);
			}
			if (payOut != null) {
				CodecUtil.writeFooter(payOut);
			}
			success = true;
		} finally {
			if (success) {
				IOUtils.close(docOut, posOut, payOut);
			} else {
				IOUtils.closeWhileHandlingException(docOut, posOut, payOut);
			}
			docOut = posOut = payOut = null;
		}
	}
}
