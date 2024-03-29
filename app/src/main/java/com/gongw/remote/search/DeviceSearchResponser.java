package com.gongw.remote.search;

import android.os.Build;
import android.util.Log;

import com.gongw.remote.RemoteConst;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * 用于响应局域网设备搜索
 */
public class DeviceSearchResponser {

    private static SearchRespThread searchRespThread;
    public static String AdminIP;
    /**
     * 启动响应线程，收到设备搜索命令后，自动响应
     */
    public static void open() {
        if (searchRespThread == null) {
            searchRespThread = new SearchRespThread();
            searchRespThread.start();
        }
    }

    /**
     * 停止响应
     */
    public static void close() {
        if (searchRespThread != null) {
            searchRespThread.destory();
            searchRespThread = null;
        }
    }

    private static class SearchRespThread extends Thread {

        DatagramSocket socket;
        volatile boolean openFlag;

        public void destory() {
            if (socket != null) {
                socket.close();
                socket = null;
            }
            openFlag = false;
        }

        @Override
        public void run() {
            try {
                //指定接收数据包的端口
                socket = new DatagramSocket(RemoteConst.DEVICE_SEARCH_PORT);
                byte[] buf = new byte[1024];
                DatagramPacket recePacket = new DatagramPacket(buf, buf.length);
                openFlag = true;
                while (openFlag) {
                    Log.d("Remote","open searchResponse");
                    socket.receive(recePacket);
                    //校验数据包是否是搜索包
                    if (verifySearchData(recePacket)) {
                        Log.d("Remote","verify search response success");
                        //发送搜索应答包
                        byte[] sendData = packSearchRespData();
                        DatagramPacket sendPack = new DatagramPacket(sendData, sendData.length, recePacket.getSocketAddress());
                        AdminIP = new String(recePacket.getSocketAddress().toString());
                        socket.send(sendPack);
                        // 只回应一次，然后关掉？
                        sleep(3000);
                    }
                }
            } catch (IOException | InterruptedException e) {
                destory();
            }
        }

        /**
         * 生成搜索应答数据
         * 协议：$(1) + packType(1) + sendSeq(4) + dataLen(1) + [data]
         * packType - 报文类型
         * sendSeq - 发送序列
         * dataLen - 数据长度
         * data - 数据内容
         * @return
         */
        private byte[] packSearchRespData() {
            Log.d("Remote","packSearchRespData");
            byte[] data = new byte[1024];
            int offset = 0;
            data[offset++] = RemoteConst.PACKET_PREFIX;
            data[offset++] = RemoteConst.PACKET_TYPE_SEARCH_DEVICE_RSP;

            // 添加UUID数据
            byte[] uuid = getUuidData();
            data[offset++] = (byte) uuid.length;
            System.arraycopy(uuid, 0, data, offset, uuid.length);
            offset += uuid.length;
            byte[] retVal = new byte[offset];
            System.arraycopy(data, 0, retVal, 0, offset);
            return retVal;
        }

        /**
         * 校验搜索数据是否符合协议规范
         * 协议：$(1) + packType(1) + sendSeq(4) + dataLen(1) + [data]
         * packType - 报文类型
         * sendSeq - 发送序列
         * dataLen - 数据长度
         * data - 数据内容
         */
        private boolean verifySearchData(DatagramPacket pack) {
            byte[] data = pack.getData();
            int length = pack.getLength();
            InetAddress ip = pack.getAddress();
            String content=new String(data,0,length);
            int port =  pack.getPort();
            Log.d("Remote",String.format("Searcher Responser receive: %s \n length: %d \n IP: %s",content,length,ip.toString()));
            if (!content.equals(RemoteConst.ADMIN_SEARCHER_ID)){
                return false;
            }else{
                return true;
            }
//            if (pack.getLength() < 6) {
//                return false;
//            }
//            byte[] data = pack.getData();
//            int offset = pack.getOffset();
//            int sendSeq;
//            if (data[offset++] != '$' || data[offset++] != RemoteConst.PACKET_TYPE_SEARCH_DEVICE_REQ) {
//                return false;
//            }
//            sendSeq = data[offset++] & 0xFF;
//            sendSeq |= (data[offset++] << 8) & 0xFF00;
//            sendSeq |= (data[offset++] << 16) & 0xFF0000;
//            sendSeq |= (data[offset++] << 24) & 0xFF000000;
//            if (sendSeq < 1 || sendSeq > RemoteConst.SEARCH_DEVICE_TIMES) {
//                return false;
//            }
        }

        /**
         * 获取设备uuid
         * @return
         */
        private byte[] getUuidData() {
            return (Build.PRODUCT + Build.ID).getBytes();
        }
    }
}