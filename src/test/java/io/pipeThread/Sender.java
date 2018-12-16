package test.java.io.pipeThread;

import java.io.IOException;
import java.io.PipedOutputStream;

/**
 * Created by Administrator on 2018/9/22.
 */
public class Sender extends Thread {

    private PipedOutputStream out = new PipedOutputStream();

    public PipedOutputStream getOut() {
        return out;
    }

    public void setOut(PipedOutputStream out) {
        this.out = out;
    }

    @Override
    public void run() {
        //writerShortMessage();
        writerLongMessage();
    }

    private void writerShortMessage() {
        String msg = "This is a Message";

        try {
            System.out.println("start writer");
            out.write(msg.getBytes());
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writerLongMessage() {
        StringBuilder sb = new StringBuilder();

        //拼接1046个字节
        for (int i = 0; i < 102; i ++) {
            sb.append("0123456789");
        }
        sb.append("abcdefghijklmnopqrstuvwxyz");

        //开始写入
        String str = sb.toString();
        try {
            out.write(str.getBytes());
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
