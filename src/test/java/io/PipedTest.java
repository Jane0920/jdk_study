package test.java.io;

import test.java.io.pipeThread.Receiver;
import test.java.io.pipeThread.Sender;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * Created by Administrator on 2018/9/22.
 */
public class PipedTest {

    public static void main(String[] args) {
        Receiver receiver = new Receiver();
        Sender sender = new Sender();

        PipedInputStream in = receiver.getIn();
        PipedOutputStream out = sender.getOut();

        try {
            //也可以用 in.connect(out)的形式
            out.connect(in);

            sender.start();
            receiver.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
