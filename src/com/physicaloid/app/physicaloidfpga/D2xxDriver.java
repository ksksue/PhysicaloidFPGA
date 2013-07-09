package com.physicaloid.app.physicaloidfpga;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

public class D2xxDriver {
    private static final String TAG = D2xxDriver.class.getSimpleName();

    private Context mContext;

    private D2xxManager ftD2xx  = null;
    private FT_Device   ftDev   = null;

    private static final int    USB_OPEN_INDEX  = 0;

    public D2xxDriver(Context context) {
        mContext = context;
        try {
            ftD2xx = D2xxManager.getInstance(context);
        } catch (D2xxManager.D2xxException ex) {
            Log.e(TAG,ex.toString());
        }
    }

    boolean isOpen() {
        if(ftDev == null) return false;
        return ftDev.isOpen();
    }

    boolean open() {
        if (ftDev == null) {
            int devCount = 0;
            devCount = ftD2xx.createDeviceInfoList(mContext);

            Log.d(TAG, "Device number : " + Integer.toString(devCount));

            D2xxManager.FtDeviceInfoListNode[] deviceList = new D2xxManager.FtDeviceInfoListNode[devCount];
            ftD2xx.getDeviceInfoList(devCount, deviceList);

            if (devCount <= 0) {
                return false;
            }

            ftDev = ftD2xx.openByIndex(mContext, USB_OPEN_INDEX);
        } else {
            if (!ftDev.isOpen()) {
                synchronized (ftDev) {
                    ftDev = ftD2xx.openByIndex(mContext, USB_OPEN_INDEX);
                }
            }
        }

        if (ftDev.isOpen()) {
            synchronized (ftDev) {
                ftDev.resetDevice(); // flush any data from the device buffers
            }
            return true;
        } else {
            Toast.makeText(mContext, "Cannot open.", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    boolean close() {
        if(ftDev != null) {
            synchronized (ftDev) {
                ftDev.close();
            }
            mContext = null;
            return true;
        }
        return false;
    }

    int write(String str) {
        byte[] buf = str.getBytes();
        return write(buf, buf.length);
    }

    int write(byte[] buf, int size) {
        int indexByte=0;
        int remainBytes=size;
        int sendSize=0;
        int SEND_PACKET_SIZE = 256;

        byte[] sendPacket = new byte[SEND_PACKET_SIZE];

        for(;remainBytes > 0;) {
            if(remainBytes < SEND_PACKET_SIZE) {
                sendSize = remainBytes;
            } else {
                sendSize = SEND_PACKET_SIZE;
            }
            System.arraycopy(buf, indexByte, sendPacket, 0, sendSize);

            synchronized (ftDev) {
                ftDev.write(sendPacket, sendSize);
            }
            remainBytes -= sendSize;
            indexByte += sendSize;
        }

        return size;
    }

    int read(byte[] buf, int size, int wait_ms) {
        int ret;
        synchronized (ftDev) {
            ret = ftDev.read(buf,size,wait_ms);
        }
        return ret;
    }

    int getQueueStatus() {
        synchronized (ftDev) {
            return ftDev.getQueueStatus();
        }
    }

}
