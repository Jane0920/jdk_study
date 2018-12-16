package test.java.io.pipeThread;

import java.io.IOException;
import java.io.PipedInputStream;

/**
 * Created by Administrator on 2018/9/22.
 */
public class Receiver extends Thread {

    private PipedInputStream in = new PipedInputStream();

    public PipedInputStream getIn() {
        return in;
    }

    @Override
    public void run() {
        readMessageContinued2();
        // readMessageOnce();
    }

    private void readMessageOnce() {
        //虽然写着2048，但是以实际字节数为准
        byte[] msgByte = new byte[2028];
        try {
            System.out.println("start read");
            int len = in.read(msgByte);
            System.out.println(new String(msgByte, 0, len));
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readMessageContinued1() {
        int total = 0;

        byte[] msgByte = new byte[2028];
        int off = 0;
        int len = 2028;
        while (true) {
            try {
                int hasRead = in.read(msgByte, off, len);
                //因为管道输入流的缓冲区默认只能存放1024个字节 所以一次读取到的字节数最多为1024
                System.out.println("当前读取到的字节数：" + hasRead);
                off += hasRead;
                len -= hasRead;
                if (hasRead == -1) {
                    break;
                }
                total += hasRead;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("最终数据：" + new String(msgByte, 0, total));
    }

    // 从“管道输入流”读取>1024个字节时，就停止读取
    public void readMessageContinued2() {
        int total=0;
        while(true) {
            byte[] buf = new byte[1024];
            try {
                int len = in.read(buf);
                total += len;
                System.out.println(new String(buf,0,len));
                // 若读取的字节总数>1024，则退出循环。
                if (total > 1024)
                    break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
