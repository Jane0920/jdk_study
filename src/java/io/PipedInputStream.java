/*
 * Copyright (c) 1995, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package java.io;

/**
 * A piped input stream should be connected
 * to a piped output stream; the piped  input
 * stream then provides whatever data bytes
 * are written to the piped output  stream.
 * Typically, data is read from a <code>PipedInputStream</code>
 * object by one thread  and data is written
 * to the corresponding <code>PipedOutputStream</code>
 * by some  other thread. Attempting to use
 * both objects from a single thread is not
 * recommended, as it may deadlock the thread.
 * The piped input stream contains a buffer,
 * decoupling read operations from write operations,
 * within limits.
 * A pipe is said to be <a name="BROKEN"> <i>broken</i> </a> if a
 * thread that was providing data bytes to the connected
 * piped output stream is no longer alive.
 * PipedInputStream和PipedOutputStream分别对应管道输入流和管道输出流，用于多线程通过管道进行数据通讯。
 * 在通讯时必须管道输入流和管道输出流配套使用，其流程为：一个线程通过管道输出流PipedOutputStream写入数据，数据会通过PipedOutputStream传入到PipedInputStream，
 * 进而存储在PipedInputStream的缓冲中。然后另一个线程通过PipedInputStream读取数据，从而实现线程之间的通讯。
 * 要注意的是，通讯必须是不同线程之间的，不然容易产生死锁。
 * 管道输入流中包含了缓冲区，在一定程度上对读操作和写操作进行了解耦
 * @author  James Gosling
 * @see     java.io.PipedOutputStream
 * @since   JDK1.0
 */
public class PipedInputStream extends InputStream {
    //“管道输出流”（写入）是否被关闭
    boolean closedByWriter = false;
    //“管道输入流”（读入）是否被关闭
    volatile boolean closedByReader = false;
    //连接是否关闭
    boolean connected = false;

        /* REMIND: identification of the read and write sides needs to be
           more sophisticated.  Either using thread groups (but what about
           pipes within a thread?) or using finalization (but it may be a
           long time until the next GC). */
    //读取管道的线程
    Thread readSide;
    //写入管道的线程
    Thread writeSide;

    //默认管道的大小
    private static final int DEFAULT_PIPE_SIZE = 1024;

    /**
     * The default size of the pipe's circular input buffer.
     * 循环输入缓冲区的默认大小
     * @since   JDK1.1
     */
    // This used to be a constant before the pipe size was allowed
    // to change. This field will continue to be maintained
    // for backward compatibility.
    protected static final int PIPE_SIZE = DEFAULT_PIPE_SIZE;

    /**
     * The circular buffer into which incoming data is placed.
     * 循环缓冲区
     * @since   JDK1.1
     */
    protected byte buffer[];

    /**
     * The index of the position in the circular buffer at which the
     * next byte of data will be stored when received from the connected
     * piped output stream. <code>in&lt;0</code> implies the buffer is empty,
     * <code>in==out</code> implies the buffer is full
     * 循环缓冲区的当前被存储的位置索引，当从管道输出流中接收数据时，将存储下一个字节
     * in < 0 表示缓冲区为空，当in = out时，表示缓冲区已满，。in==out代表满，说明“写入的数据”全部被读取了。
     * @since   JDK1.1
     */
    protected int in = -1;

    /**
     * The index of the position in the circular buffer at which the next
     * byte of data will be read by this piped input stream.
     * 循环缓冲区中位置索引用于表示在管道输入流中下一个要被读取的字节索引
     * @since   JDK1.1
     */
    protected int out = 0;

    /**
     * Creates a <code>PipedInputStream</code> so
     * that it is connected to the piped output
     * stream <code>src</code>. Data bytes written
     * to <code>src</code> will then be  available
     * as input from this stream.
     * 构造函数用于和src管道输出流连接
     * @param      src   the stream to connect to.
     * @exception  IOException  if an I/O error occurs.
     */
    public PipedInputStream(PipedOutputStream src) throws IOException {
        this(src, DEFAULT_PIPE_SIZE);
    }

    /**
     * Creates a <code>PipedInputStream</code> so that it is
     * connected to the piped output stream
     * <code>src</code> and uses the specified pipe size for
     * the pipe's buffer.
     * Data bytes written to <code>src</code> will then
     * be available as input from this stream.
     *
     * @param      src   the stream to connect to.
     * @param      pipeSize the size of the pipe's buffer.
     * @exception  IOException  if an I/O error occurs.
     * @exception  IllegalArgumentException if {@code pipeSize <= 0}.
     * @since      1.6
     */
    public PipedInputStream(PipedOutputStream src, int pipeSize)
            throws IOException {
         initPipe(pipeSize);
         connect(src);
    }

    /**
     * Creates a <code>PipedInputStream</code> so
     * that it is not yet {@linkplain #connect(java.io.PipedOutputStream)
     * connected}.
     * It must be {@linkplain java.io.PipedOutputStream#connect(
     * java.io.PipedInputStream) connected} to a
     * <code>PipedOutputStream</code> before being used.
     * 构造函数，缓冲区大小为默认值，无参的方式，在之前肯定已经进行过连接
     */
    public PipedInputStream() {
        initPipe(DEFAULT_PIPE_SIZE);
    }

    /**
     * Creates a <code>PipedInputStream</code> so that it is not yet
     * {@linkplain #connect(java.io.PipedOutputStream) connected} and
     * uses the specified pipe size for the pipe's buffer.
     * It must be {@linkplain java.io.PipedOutputStream#connect(
     * java.io.PipedInputStream)
     * connected} to a <code>PipedOutputStream</code> before being used.
     *
     * @param      pipeSize the size of the pipe's buffer.
     * @exception  IllegalArgumentException if {@code pipeSize <= 0}.
     * @since      1.6
     */
    public PipedInputStream(int pipeSize) {
        initPipe(pipeSize);
    }

    /**
     * 初始化管道缓冲区
     * @param pipeSize
     */
    private void initPipe(int pipeSize) {
        //判断管道大小是否小于等于0
         if (pipeSize <= 0) {
             //如果是，则抛出非法参数异常
            throw new IllegalArgumentException("Pipe Size <= 0");
         }
         //否则初始化缓存区大小
         buffer = new byte[pipeSize];
    }

    /**
     * Causes this piped input stream to be connected
     * to the piped  output stream <code>src</code>.
     * If this object is already connected to some
     * other piped output  stream, an <code>IOException</code>
     * is thrown.
     * <p>
     * If <code>src</code> is an
     * unconnected piped output stream and <code>snk</code>
     * is an unconnected piped input stream, they
     * may be connected by either the call:
     *
     * <pre><code>snk.connect(src)</code> </pre>
     * <p>
     * or the call:
     *
     * <pre><code>src.connect(snk)</code> </pre>
     * <p>
     * The two calls have the same effect.
     *
     * @param      src   The piped output stream to connect to.
     * @exception  IOException  if an I/O error occurs.
     */
    public void connect(PipedOutputStream src) throws IOException {
        src.connect(this);
    }

    /**
     * Receives a byte of data.  This method will block if no input is
     * available.
     * 它只会在PipedOutputStream的write(int b)中会被调用
     * @param b the byte being received
     * @exception IOException If the pipe is <a href="#BROKEN"> <code>broken</code></a>,
     *          {@link #connect(java.io.PipedOutputStream) unconnected},
     *          closed, or if an I/O error occurs.
     * @since     JDK1.1
     */
    protected synchronized void receive(int b) throws IOException {
        //检查接收状态
        checkStateForReceive();
        //获取写入方线程 获取到了但是未使用
        writeSide = Thread.currentThread();
        //如果缓冲区当前可写入位置和当前读取的位置相等，无可读取的数据
        if (in == out)
            //等待唤醒写入线程 ?不明白
            awaitSpace();
        //如果写入缓冲区的位置小于0，可能是读取线程赋的值，也可能刚开始，说明当前缓冲区无数据
        if (in < 0) {
            //则初始化
            in = 0;
            out = 0;
        }
        //将b写入缓冲区
        buffer[in++] = (byte)(b & 0xFF);
        //如果下一个写入缓冲区的位置已经大于缓冲区大小
        if (in >= buffer.length) {
            //基于0值
            in = 0;
        }
    }

    /**
     * Receives data into an array of bytes.  This method will
     * block until some input is available.
     * @param b the buffer into which the data is received
     * @param off the start offset of the data
     * @param len the maximum number of bytes received
     * @exception IOException If the pipe is <a href="#BROKEN"> broken</a>,
     *           {@link #connect(java.io.PipedOutputStream) unconnected},
     *           closed,or if an I/O error occurs.
     */
    synchronized void receive(byte b[], int off, int len)  throws IOException {
        //检测接收状态
        checkStateForReceive();
        //获取当前写入的线程
        writeSide = Thread.currentThread();
        //需要转入的字节数，开始为len
        int bytesToTransfer = len;
        //如果需要转入的字节数大于0，则进行写入操作
        while (bytesToTransfer > 0) {
            //判断是否具有可读取数据
            if (in == out)
                //如果没有，等待唤醒写入线程
                awaitSpace();
            //可写入缓冲区字节数量
            int nextTransferAmount = 0;
            if (out < in) {
                nextTransferAmount = buffer.length - in;
            } else if (in < out) {
                if (in == -1) {
                    in = out = 0;
                    nextTransferAmount = buffer.length - in;
                } else {
                    nextTransferAmount = out - in;
                }
            }
            if (nextTransferAmount > bytesToTransfer)
                nextTransferAmount = bytesToTransfer;
            assert(nextTransferAmount > 0);
            System.arraycopy(b, off, buffer, in, nextTransferAmount);
            bytesToTransfer -= nextTransferAmount;
            off += nextTransferAmount;
            in += nextTransferAmount;
            if (in >= buffer.length) {
                in = 0;
            }
        }
    }

    /**
     * 检查接收状态
     * @throws IOException
     */
    private void checkStateForReceive() throws IOException {
        //是否已经连接
        if (!connected) {
            //如果不是抛出IO异常
            throw new IOException("Pipe not connected");
        } else if (closedByWriter || closedByReader) {
            //写入或者写出是否已经关闭
            //如果是，则抛出IO异常
            throw new IOException("Pipe closed");
        } else if (readSide != null && !readSide.isAlive()) {
            //如果写线程不为空但是不属于存活状态
            //如果是，还是抛出IO异常
            throw new IOException("Read end dead");
        }
    }

    /**
     * 等待缓冲区位置空闲
     * @throws IOException
     */
    private void awaitSpace() throws IOException {
        //循环判断是否缓冲区存在空位置
        while (in == out) {
            //检测当前接收状态
            checkStateForReceive();

            /* full: kick any waiting readers */
            //
            notifyAll();
            try {
                wait(1000);
            } catch (InterruptedException ex) {
                throw new java.io.InterruptedIOException();
            }
        }
    }

    /**
     * Notifies all waiting threads that the last byte of data has been
     * received.
     * 通知所有等待的线程最后一个字节已经被接收
     */
    synchronized void receivedLast() {
        //写入标识关闭
        closedByWriter = true;
        //唤醒所有等待线程
        notifyAll();
    }

    /**
     * Reads the next byte of data from this piped input stream. The
     * value byte is returned as an <code>int</code> in the range
     * <code>0</code> to <code>255</code>.
     * This method blocks until input data is available, the end of the
     * stream is detected, or an exception is thrown.
     * 通过管道输入流读取下一个字节，读取的字节值为0到255 的int类型
     * 方法会处于阻塞状态直到有数据可读
     * @return     the next byte of data, or <code>-1</code> if the end of the
     *             stream is reached.
     * @exception  IOException  if the pipe is
     *           {@link #connect(java.io.PipedOutputStream) unconnected},
     *           <a href="#BROKEN"> <code>broken</code></a>, closed,
     *           or if an I/O error occurs.
     */
    public synchronized int read()  throws IOException {
        //如果
        // 1.未连接
        // 2.或者读取标识已关闭
        // 3.或者写入线程不为空但是未存活状态并且写入标识未关闭并且写入位置为-1 表示写入已无用
        // 则抛出IO异常
        if (!connected) {
            throw new IOException("Pipe not connected");
        } else if (closedByReader) {
            throw new IOException("Pipe closed");
        } else if (writeSide != null && !writeSide.isAlive()
                   && !closedByWriter && (in < 0)) {
            throw new IOException("Write end dead");
        }

        //获取读线程
        readSide = Thread.currentThread();
        int trials = 2;
        //判断是否有写入数据
        while (in < 0) {
            //判断写标识是否关闭，为什么写标识关闭了就要返回-1？
            if (closedByWriter) {
                /* closed by writer, return EOF */
                return -1;
            }
            if ((writeSide != null) && (!writeSide.isAlive()) && (--trials < 0)) {
                //如果写入线程不为空，但是写入线程不属于启动状态并且未死亡状态，并且尝试次数已经小于0,
                //则抛出IO异常
                throw new IOException("Pipe broken");
            }
            /* might be a writer waiting */
            //唤醒所有写入线程，写入数据
            notifyAll();
            try {
                //等待1秒再次判断
                wait(1000);
            } catch (InterruptedException ex) {
                throw new java.io.InterruptedIOException();
            }
        }
        //通过上述循环如果能执行到这里，说明缓冲区已经写入过数据
        //则读取一个字节 转为int形式
        int ret = buffer[out++] & 0xFF;
        //判断下一个要读取的位置是否已经超过缓冲区的大小
        if (out >= buffer.length) {
            //如果已经超过，则从循环缓冲区的0位置开始读取
            out = 0;
        }
        //如果此时可读取的数据为空
        if (in == out) {
            //则赋值in为-1，用于下次判断是否空数据
            /* now empty */
            in = -1;
        }

        //返回读取到的值
        return ret;
    }

    /**
     * Reads up to <code>len</code> bytes of data from this piped input
     * stream into an array of bytes. Less than <code>len</code> bytes
     * will be read if the end of the data stream is reached or if
     * <code>len</code> exceeds the pipe's buffer size.
     * If <code>len </code> is zero, then no bytes are read and 0 is returned;
     * otherwise, the method blocks until at least 1 byte of input is
     * available, end of the stream has been detected, or an exception is
     * thrown.
     * 读取len长度的字节从缓冲区并且赋值到b数组中，
     * 如果当前已经读取到缓冲区末尾或者len的长度超过了缓冲区的大小，那么实际读取的字节数将小于len
     * 如果len为0，那么将返回0
     * 方法将被阻塞直到至少有一个字节可以被读取，或者已经读取到字节流的末尾，或者有异常抛出
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset in the destination array <code>b</code>
     * @param      len   the maximum number of bytes read.
     * @return     the total number of bytes read into the buffer, or
     *             <code>-1</code> if there is no more data because the end of
     *             the stream has been reached.
     * @exception  NullPointerException If <code>b</code> is <code>null</code>.
     * @exception  IndexOutOfBoundsException If <code>off</code> is negative,
     * <code>len</code> is negative, or <code>len</code> is greater than
     * <code>b.length - off</code>
     * @exception  IOException if the pipe is <a href="#BROKEN"> <code>broken</code></a>,
     *           {@link #connect(java.io.PipedOutputStream) unconnected},
     *           closed, or if an I/O error occurs.
     */
    public synchronized int read(byte b[], int off, int len)  throws IOException {
        //如果 b数组为空
        if (b == null) {
            //抛出空指针异常
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            //赋值到b数组的开始位置小于0，或者要读取的长度小于0，或者要读取的长度超过b可赋值的长度
            //抛出超出下标的异常
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            //如果要读取的长度等于0
            //则直接返回0
            return 0;
        }

        /* possibly wait on the first character */
        //首先读取一个字节
        int c = read();
        //如果无可读取的字节
        if (c < 0) {
            //返回-1
            return -1;
        }
        //否则对b赋值第一个值
        b[off] = (byte) c;
        int rlen = 1;
        //当有可读的数据，并且还需要读取的长度大于1
        while ((in >= 0) && (len > 1)) {

            int available;

            if (in > out) {
                //如果写入的下标大于读出的下标
                //则可读取的字节数为(buffer.length - out), (in - out)最小值
                available = Math.min((buffer.length - out), (in - out));
            } else {
                //否则 可读取字节数为buffer.length - out
                available = buffer.length - out;
            }

            // A byte is read beforehand outside the loop
            //如果可读取的字节数大于现在需要读取的字节数
            if (available > (len - 1)) {
                available = len - 1;
            }
            System.arraycopy(buffer, out, b, off + rlen, available);
            out += available;
            rlen += available;
            len -= available;

            if (out >= buffer.length) {
                out = 0;
            }
            if (in == out) {
                /* now empty */
                in = -1;
            }
        }
        return rlen;
    }

    /**
     * Returns the number of bytes that can be read from this input
     * stream without blocking.
     *
     * @return the number of bytes that can be read from this input stream
     *         without blocking, or {@code 0} if this input stream has been
     *         closed by invoking its {@link #close()} method, or if the pipe
     *         is {@link #connect(java.io.PipedOutputStream) unconnected}, or
     *          <a href="#BROKEN"> <code>broken</code></a>.
     *
     * @exception  IOException  if an I/O error occurs.
     * @since   JDK1.0.2
     */
    public synchronized int available() throws IOException {
        if(in < 0)
            return 0;
        else if(in == out)
            return buffer.length;
        else if (in > out)
            return in - out;
        else
            return in + buffer.length - out;
    }

    /**
     * Closes this piped input stream and releases any system resources
     * associated with the stream.
     *
     * @exception  IOException  if an I/O error occurs.
     */
    public void close()  throws IOException {
        closedByReader = true;
        synchronized (this) {
            in = -1;
        }
    }
}
