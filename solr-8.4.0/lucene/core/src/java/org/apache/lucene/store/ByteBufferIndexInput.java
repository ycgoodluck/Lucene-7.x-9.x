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
package org.apache.lucene.store;


import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;

/**
 * Base IndexInput implementation that uses an array
 * of ByteBuffers to represent a file.
 * <p>
 * Because Java's ByteBuffer uses an int to address the
 * values, it's necessary to access a file greater
 * Integer.MAX_VALUE in size using multiple byte buffers.
 * <p>
 * For efficiency, this class requires that the buffers
 * are a power-of-two (<code>chunkSizePower</code>).
 */
public abstract class ByteBufferIndexInput extends IndexInput implements RandomAccessInput {
	private static final LongBuffer EMPTY_LONGBUFFER = LongBuffer.allocate(0);

	protected final long length;
	protected final long chunkSizeMask;
	protected final int chunkSizePower;
	protected final ByteBufferGuard guard;

	protected ByteBuffer[] buffers;
	protected int curBufIndex = -1;
	protected ByteBuffer curBuf; // redundant for speed: buffers[curBufIndex]
	private LongBuffer[] curLongBufferViews;

	protected boolean isClone = false;

	public static ByteBufferIndexInput newInstance(String resourceDescription, ByteBuffer[] buffers, long length, int chunkSizePower, ByteBufferGuard guard) {
		if (buffers.length == 1) {
			return new SingleBufferImpl(resourceDescription, buffers[0], length, chunkSizePower, guard);
		} else {
			return new MultiBufferImpl(resourceDescription, buffers, 0, length, chunkSizePower, guard);
		}
	}

	ByteBufferIndexInput(String resourceDescription, ByteBuffer[] buffers, long length, int chunkSizePower, ByteBufferGuard guard) {
		super(resourceDescription);
		this.buffers = buffers;
		this.length = length;
		this.chunkSizePower = chunkSizePower;
		this.chunkSizeMask = (1L << chunkSizePower) - 1L;
		this.guard = guard;
		assert chunkSizePower >= 0 && chunkSizePower <= 30;
		assert (length >>> chunkSizePower) < Integer.MAX_VALUE;
	}

	protected void setCurBuf(ByteBuffer curBuf) {
		this.curBuf = curBuf;
		curLongBufferViews = null;
	}

	@Override
	public final byte readByte() throws IOException {
		try {
			return guard.getByte(curBuf);
		} catch (BufferUnderflowException e) {
			do {
				curBufIndex++;
				if (curBufIndex >= buffers.length) {
					throw new EOFException("read past EOF: " + this);
				}
				setCurBuf(buffers[curBufIndex]);
				curBuf.position(0);
			} while (!curBuf.hasRemaining());
			return guard.getByte(curBuf);
		} catch (NullPointerException npe) {
			throw new AlreadyClosedException("Already closed: " + this);
		}
	}

	@Override
	public final void readBytes(byte[] b, int offset, int len) throws IOException {
		try {
			guard.getBytes(curBuf, b, offset, len);
		} catch (BufferUnderflowException e) {
			int curAvail = curBuf.remaining();
			while (len > curAvail) {
				guard.getBytes(curBuf, b, offset, curAvail);
				len -= curAvail;
				offset += curAvail;
				curBufIndex++;
				if (curBufIndex >= buffers.length) {
					throw new EOFException("read past EOF: " + this);
				}
				setCurBuf(buffers[curBufIndex]);
				curBuf.position(0);
				curAvail = curBuf.remaining();
			}
			guard.getBytes(curBuf, b, offset, len);
		} catch (NullPointerException npe) {
			throw new AlreadyClosedException("Already closed: " + this);
		}
	}

	@Override
	public void readLELongs(long[] dst, int offset, int length) throws IOException {
		// ByteBuffer#getLong could work but it has some per-long overhead and there
		// is no ByteBuffer#getLongs to read multiple longs at once. So we use the
		// below trick in order to be able to leverage LongBuffer#get(long[]) to
		// read multiple longs at once with as little overhead as possible.
		if (curLongBufferViews == null) {
			// readLELongs is only used for postings today, so we compute the long
			// views lazily so that other data-structures don't have to pay for the
			// associated initialization/memory overhead.
			curLongBufferViews = new LongBuffer[Long.BYTES];
			for (int i = 0; i < Long.BYTES; ++i) {
				// Compute a view for each possible alignment. We cache these views
				// because #asLongBuffer() has some cost that we don't want to pay on
				// each invocation of #readLELongs.
				if (i < curBuf.limit()) {
					ByteBuffer dup = curBuf.duplicate().order(ByteOrder.LITTLE_ENDIAN);
					dup.position(i);
					curLongBufferViews[i] = dup.asLongBuffer();
				} else {
					curLongBufferViews[i] = EMPTY_LONGBUFFER;
				}
			}
		}
		try {
			final int position = curBuf.position();
			LongBuffer longBuffer = curLongBufferViews[position & 0x07];
			longBuffer.position(position >>> 3);
			guard.getLongs(longBuffer, dst, offset, length);
			// if the above call succeeded, then we know the below sum cannot overflow
			curBuf.position(position + (length << 3));
		} catch (BufferUnderflowException e) {
			super.readLELongs(dst, offset, length);
		} catch (NullPointerException npe) {
			throw new AlreadyClosedException("Already closed: " + this);
		}
	}

	@Override
	public final short readShort() throws IOException {
		try {
			return guard.getShort(curBuf);
		} catch (BufferUnderflowException e) {
			return super.readShort();
		} catch (NullPointerException npe) {
			throw new AlreadyClosedException("Already closed: " + this);
		}
	}

	@Override
	public final int readInt() throws IOException {
		try {
			return guard.getInt(curBuf);
		} catch (BufferUnderflowException e) {
			return super.readInt();
		} catch (NullPointerException npe) {
			throw new AlreadyClosedException("Already closed: " + this);
		}
	}

	@Override
	public final long readLong() throws IOException {
		try {
			return guard.getLong(curBuf);
		} catch (BufferUnderflowException e) {
			return super.readLong();
		} catch (NullPointerException npe) {
			throw new AlreadyClosedException("Already closed: " + this);
		}
	}

	@Override
	public long getFilePointer() {
		try {
			return (((long) curBufIndex) << chunkSizePower) + curBuf.position();
		} catch (NullPointerException npe) {
			throw new AlreadyClosedException("Already closed: " + this);
		}
	}

	@Override
	public void seek(long pos) throws IOException {
		// we use >> here to preserve negative, so we will catch AIOOBE,
		// in case pos + offset overflows.
		final int bi = (int) (pos >> chunkSizePower);
		try {
			if (bi == curBufIndex) {
				curBuf.position((int) (pos & chunkSizeMask));
			} else {
				final ByteBuffer b = buffers[bi];
				b.position((int) (pos & chunkSizeMask));
				// write values, on exception all is unchanged
				this.curBufIndex = bi;
				setCurBuf(b);
			}
		} catch (ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
			throw new EOFException("seek past EOF: " + this);
		} catch (NullPointerException npe) {
			throw new AlreadyClosedException("Already closed: " + this);
		}
	}

	@Override
	public byte readByte(long pos) throws IOException {
		try {
			final int bi = (int) (pos >> chunkSizePower);
			return guard.getByte(buffers[bi], (int) (pos & chunkSizeMask));
		} catch (IndexOutOfBoundsException ioobe) {
			throw new EOFException("seek past EOF: " + this);
		} catch (NullPointerException npe) {
			throw new AlreadyClosedException("Already closed: " + this);
		}
	}

	// used only by random access methods to handle reads across boundaries
	private void setPos(long pos, int bi) throws IOException {
		try {
			final ByteBuffer b = buffers[bi];
			b.position((int) (pos & chunkSizeMask));
			this.curBufIndex = bi;
			setCurBuf(b);
		} catch (ArrayIndexOutOfBoundsException | IllegalArgumentException aioobe) {
			throw new EOFException("seek past EOF: " + this);
		} catch (NullPointerException npe) {
			throw new AlreadyClosedException("Already closed: " + this);
		}
	}

	@Override
	public short readShort(long pos) throws IOException {
		final int bi = (int) (pos >> chunkSizePower);
		try {
			return guard.getShort(buffers[bi], (int) (pos & chunkSizeMask));
		} catch (IndexOutOfBoundsException ioobe) {
			// either it's a boundary, or read past EOF, fall back:
			setPos(pos, bi);
			return readShort();
		} catch (NullPointerException npe) {
			throw new AlreadyClosedException("Already closed: " + this);
		}
	}

	@Override
	public int readInt(long pos) throws IOException {
		final int bi = (int) (pos >> chunkSizePower);
		try {
			return guard.getInt(buffers[bi], (int) (pos & chunkSizeMask));
		} catch (IndexOutOfBoundsException ioobe) {
			// either it's a boundary, or read past EOF, fall back:
			setPos(pos, bi);
			return readInt();
		} catch (NullPointerException npe) {
			throw new AlreadyClosedException("Already closed: " + this);
		}
	}

	@Override
	public long readLong(long pos) throws IOException {
		final int bi = (int) (pos >> chunkSizePower);
		try {
			return guard.getLong(buffers[bi], (int) (pos & chunkSizeMask));
		} catch (IndexOutOfBoundsException ioobe) {
			// either it's a boundary, or read past EOF, fall back:
			setPos(pos, bi);
			return readLong();
		} catch (NullPointerException npe) {
			throw new AlreadyClosedException("Already closed: " + this);
		}
	}

	@Override
	public final long length() {
		return length;
	}

	@Override
	public final ByteBufferIndexInput clone() {
		final ByteBufferIndexInput clone = buildSlice((String) null, 0L, this.length);
		try {
			clone.seek(getFilePointer());
		} catch (IOException ioe) {
			throw new AssertionError(ioe);
		}

		return clone;
	}

	/**
	 * Creates a slice of this index input, with the given description, offset, and length. The slice is seeked to the beginning.
	 */
	@Override
	public final ByteBufferIndexInput slice(String sliceDescription, long offset, long length) {
		if (offset < 0 || length < 0 || offset + length > this.length) {
			throw new IllegalArgumentException("slice() " + sliceDescription + " out of bounds: offset=" + offset + ",length=" + length + ",fileLength=" + this.length + ": " + this);
		}

		return buildSlice(sliceDescription, offset, length);
	}

	/**
	 * Builds the actual sliced IndexInput (may apply extra offset in subclasses).
	 **/
	protected ByteBufferIndexInput buildSlice(String sliceDescription, long offset, long length) {
		if (buffers == null) {
			throw new AlreadyClosedException("Already closed: " + this);
		}

		final ByteBuffer newBuffers[] = buildSlice(buffers, offset, length);
		final int ofs = (int) (offset & chunkSizeMask);

		final ByteBufferIndexInput clone = newCloneInstance(getFullSliceDescription(sliceDescription), newBuffers, ofs, length);
		clone.isClone = true;

		return clone;
	}

	/**
	 * Factory method that creates a suitable implementation of this class for the given ByteBuffers.
	 */
	@SuppressWarnings("resource")
	protected ByteBufferIndexInput newCloneInstance(String newResourceDescription, ByteBuffer[] newBuffers, int offset, long length) {
		if (newBuffers.length == 1) {
			newBuffers[0].position(offset);
			return new SingleBufferImpl(newResourceDescription, newBuffers[0].slice(), length, chunkSizePower, this.guard);
		} else {
			return new MultiBufferImpl(newResourceDescription, newBuffers, offset, length, chunkSizePower, guard);
		}
	}

	/**
	 * Returns a sliced view from a set of already-existing buffers:
	 * the last buffer's limit() will be correct, but
	 * you must deal with offset separately (the first buffer will not be adjusted)
	 */
	private ByteBuffer[] buildSlice(ByteBuffer[] buffers, long offset, long length) {
		final long sliceEnd = offset + length;

		final int startIndex = (int) (offset >>> chunkSizePower);
		final int endIndex = (int) (sliceEnd >>> chunkSizePower);

		// we always allocate one more slice, the last one may be a 0 byte one
		final ByteBuffer slices[] = new ByteBuffer[endIndex - startIndex + 1];

		for (int i = 0; i < slices.length; i++) {
			slices[i] = buffers[startIndex + i].duplicate();
		}

		// set the last buffer's limit for the sliced view.
		slices[slices.length - 1].limit((int) (sliceEnd & chunkSizeMask));

		return slices;
	}

	@Override
	public final void close() throws IOException {
		try {
			if (buffers == null) return;

			// make local copy, then un-set early
			final ByteBuffer[] bufs = buffers;
			unsetBuffers();

			if (isClone) return;

			// tell the guard to invalidate and later unmap the bytebuffers (if supported):
			guard.invalidateAndUnmap(bufs);
		} finally {
			unsetBuffers();
		}
	}

	/**
	 * Called to remove all references to byte buffers, so we can throw AlreadyClosed on NPE.
	 */
	private void unsetBuffers() {
		buffers = null;
		curBuf = null;
		curBufIndex = 0;
		curLongBufferViews = null;
	}

	/**
	 * Optimization of ByteBufferIndexInput for when there is only one buffer
	 */
	static final class SingleBufferImpl extends ByteBufferIndexInput {

		SingleBufferImpl(String resourceDescription, ByteBuffer buffer, long length, int chunkSizePower, ByteBufferGuard guard) {
			super(resourceDescription, new ByteBuffer[]{buffer}, length, chunkSizePower, guard);
			this.curBufIndex = 0;
			setCurBuf(buffer);
			buffer.position(0);
		}

		// TODO: investigate optimizing readByte() & Co?

		@Override
		public void seek(long pos) throws IOException {
			try {
				curBuf.position((int) pos);
			} catch (IllegalArgumentException e) {
				if (pos < 0) {
					throw new IllegalArgumentException("Seeking to negative position: " + this, e);
				} else {
					throw new EOFException("seek past EOF: " + this);
				}
			} catch (NullPointerException npe) {
				throw new AlreadyClosedException("Already closed: " + this);
			}
		}

		@Override
		public long getFilePointer() {
			try {
				return curBuf.position();
			} catch (NullPointerException npe) {
				throw new AlreadyClosedException("Already closed: " + this);
			}
		}

		@Override
		public byte readByte(long pos) throws IOException {
			try {
				return guard.getByte(curBuf, (int) pos);
			} catch (IllegalArgumentException e) {
				if (pos < 0) {
					throw new IllegalArgumentException("Seeking to negative position: " + this, e);
				} else {
					throw new EOFException("seek past EOF: " + this);
				}
			} catch (NullPointerException npe) {
				throw new AlreadyClosedException("Already closed: " + this);
			}
		}

		@Override
		public short readShort(long pos) throws IOException {
			try {
				return guard.getShort(curBuf, (int) pos);
			} catch (IllegalArgumentException e) {
				if (pos < 0) {
					throw new IllegalArgumentException("Seeking to negative position: " + this, e);
				} else {
					throw new EOFException("seek past EOF: " + this);
				}
			} catch (NullPointerException npe) {
				throw new AlreadyClosedException("Already closed: " + this);
			}
		}

		@Override
		public int readInt(long pos) throws IOException {
			try {
				return guard.getInt(curBuf, (int) pos);
			} catch (IllegalArgumentException e) {
				if (pos < 0) {
					throw new IllegalArgumentException("Seeking to negative position: " + this, e);
				} else {
					throw new EOFException("seek past EOF: " + this);
				}
			} catch (NullPointerException npe) {
				throw new AlreadyClosedException("Already closed: " + this);
			}
		}

		@Override
		public long readLong(long pos) throws IOException {
			try {
				return guard.getLong(curBuf, (int) pos);
			} catch (IllegalArgumentException e) {
				if (pos < 0) {
					throw new IllegalArgumentException("Seeking to negative position: " + this, e);
				} else {
					throw new EOFException("seek past EOF: " + this);
				}
			} catch (NullPointerException npe) {
				throw new AlreadyClosedException("Already closed: " + this);
			}
		}

	}

	/**
	 * This class adds offset support to ByteBufferIndexInput, which is needed for slices.
	 */
	static final class MultiBufferImpl extends ByteBufferIndexInput {
		private final int offset;

		MultiBufferImpl(String resourceDescription, ByteBuffer[] buffers, int offset, long length, int chunkSizePower,
										ByteBufferGuard guard) {
			super(resourceDescription, buffers, length, chunkSizePower, guard);
			this.offset = offset;
			try {
				seek(0L);
			} catch (IOException ioe) {
				throw new AssertionError(ioe);
			}
		}

		@Override
		public void seek(long pos) throws IOException {
			assert pos >= 0L;
			super.seek(pos + offset);
		}

		@Override
		public long getFilePointer() {
			return super.getFilePointer() - offset;
		}

		@Override
		public byte readByte(long pos) throws IOException {
			return super.readByte(pos + offset);
		}

		@Override
		public short readShort(long pos) throws IOException {
			return super.readShort(pos + offset);
		}

		@Override
		public int readInt(long pos) throws IOException {
			return super.readInt(pos + offset);
		}

		@Override
		public long readLong(long pos) throws IOException {
			return super.readLong(pos + offset);
		}

		@Override
		protected ByteBufferIndexInput buildSlice(String sliceDescription, long ofs, long length) {
			return super.buildSlice(sliceDescription, this.offset + ofs, length);
		}
	}
}
