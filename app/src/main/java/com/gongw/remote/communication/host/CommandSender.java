    package com.gongw.remote.communication.host;

    import android.util.Log;

    import com.gongw.remote.RemoteConst;
    import com.gongw.remote.communication.CommunicationKey;

    import java.io.BufferedInputStream;
    import java.io.IOException;
    import java.io.InputStream;
    import java.io.ObjectInputStream;
    import java.io.ObjectOutputStream;
    import java.io.OutputStream;
    import java.net.InetSocketAddress;
    import java.net.Socket;
    import java.nio.charset.Charset;
    import java.util.concurrent.RejectedExecutionException;
    import java.util.concurrent.RejectedExecutionHandler;
    import java.util.concurrent.SynchronousQueue;
    import java.util.concurrent.ThreadPoolExecutor;
    import java.util.concurrent.TimeUnit;
    import java.util.logging.Level;

    /**
     * 用于发送命令
     * Created by gw on 2017/11/4.
     */
    public class CommandSender {
        private static ThreadPoolExecutor threadPool = new ThreadPoolExecutor(10, 10, 1, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new SendCommandThreadFactory(), new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                throw new RejectedExecutionException();
            }
        });


        public static void addCommand(final Command command){
            addTask(new CommandRunnable(command));
        }

        private static void addTask(CommandRunnable runnable){
            try{
                threadPool.execute(runnable);
            }catch (RejectedExecutionException e){
                e.printStackTrace();
                if(runnable.command.getCallback()!=null){
                    runnable.command.getCallback().onError("command is rejected");
                }
            }
        }

        private static class CommandRunnable implements Runnable{

            Command command;

            public CommandRunnable(Command command){
                this.command = command;
            }

//            @Override
//            public void run() {
//                ObjectInputStream is = null;
//                ObjectOutputStream os = null;
//
//                Log.d("Remote", String.format("CommandSender.Runnable %s",command.getContent()));
//                Socket socket = new Socket();
//                try {
//
//                    socket.connect(new InetSocketAddress(command.getDestIp(), RemoteConst.COMMAND_RECEIVE_PORT));
//                    Log.d("Remote", String.format("Admin successfully connected socket"));
//                    is = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
//                    os = new ObjectOutputStream(socket.getOutputStream());
//
//                    Log.d("Remote", "admin os.write ");
//                    os.writeObject(command);
//                    os.flush();
//
//                    Object obj = is.readObject();
//                    Command user = (Command) obj;
//                    Log.d("Remote", "admin receive: " + user.getContent());
//                    // 将网络层收到的内容传出
//                    if(user!=null){
//                        command.getCallback().onSuccess(user.getContent());
//                    }
//
//                } catch (IOException ex) {
//                    Log.d("Remote",String.format("%s"), ex);
//                } catch(ClassNotFoundException ex) {
//                    Log.d("Remote",String.format("%s"), ex);
//                } finally {
//                    try {
//                        is.close();
//                    } catch(Exception ex) {}
//                    try {
//                        os.close();
//                    } catch(Exception ex) {}
//                    try {
//                        socket.close();
//                    } catch(Exception ex) {}
//                }
//            }


            @Override
            public void run() {
                Socket socket = new Socket();
                try {
                    socket.connect(new InetSocketAddress(command.getDestIp(), RemoteConst.COMMAND_RECEIVE_PORT));
                    OutputStream os = socket.getOutputStream();
                    InputStream is = socket.getInputStream();
                    byte[] buffer = new byte[1024*8];
                    //发送命令内容
                    os.write(command.getContent().getBytes());
                    os.write(CommunicationKey.EOF.getBytes());
                    if(command.getCallback()!=null){
                        command.getCallback().onRequest(command.getContent());
                    }
                    //读取应答内容
                    int i=0;
                    while (true) {
                        buffer[i] = (byte) is.read();
                        if(buffer[i] == -1){
                            if(command.getCallback()!=null){
                                command.getCallback().onError("get response failed");
                            }
                            break;
                        }
                        if((char)buffer[i] != CommunicationKey.EOF.charAt(0)){
                            i++;
                        }else{
                            String response = new String(buffer, 0, i+1, Charset.defaultCharset()).replace(CommunicationKey.EOF, "");
                            if (response.startsWith(CommunicationKey.RESPONSE_OK)) {
                                if(command.getCallback()!=null){
                                    command.getCallback().onSuccess(response.replace(CommunicationKey.RESPONSE_OK, ""));
                                }
                                break;
                            }else if (response.startsWith(CommunicationKey.RESPONSE_ECHO)) {
                                if(command.getCallback()!=null){
                                    command.getCallback().onEcho(response.replace(CommunicationKey.RESPONSE_ECHO, ""));
                                }
                                break;
                            }else if (response.startsWith(CommunicationKey.RESPONSE_ERROR)) {
                                if(command.getCallback()!=null){
                                    command.getCallback().onError(response.replace(CommunicationKey.RESPONSE_ERROR, ""));
                                }
                                break;
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if(command.getCallback()!=null){
                        command.getCallback().onError(e.getMessage());
                    }
                }finally {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }
