package com.physicaloid.app.physicaloidfpga;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class PhysicaloidFpgaActivity extends Activity {
    private static final String TAG = PhysicaloidFpgaActivity.class.getSimpleName();
    private static final boolean DEBUG_SHOW = true;

    private Button btStart;
    private TextView tvLog;

    private EditText etWriteAddr;
    private EditText etWriteData;
    private EditText etReadAddr;

    private D2xxDriver mFTDI;
    private AvalonSTParser mAvalonSt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_physicaloid_fpga);

        btStart = (Button) findViewById(R.id.btStart);
        tvLog = (TextView) findViewById(R.id.tvLog);
        etWriteAddr = (EditText) findViewById(R.id.etWriteAddr);
        etWriteData = (EditText) findViewById(R.id.etWriteData);
        etReadAddr = (EditText) findViewById(R.id.etReadAddr);

        mFTDI = new D2xxDriver(getApplicationContext());
        mAvalonSt = new AvalonSTParser();
    }

    @Override
    public void onResume() {
        super.onResume();
        mFTDI.open();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ReadThreadStop();
        mFTDI.close();
    }

    public void onClickStart(View v) {
        byte[] rbfBuf;
        if(!mFTDI.isOpen()) {
            if(!mFTDI.open()) {
                Toast.makeText(this, "Cannot open", Toast.LENGTH_LONG).show();
                return;
            }
        }
//        rbfBuf = getRbfBytes("/sdcard/fpga/test.rbf");
        rbfBuf = getRbfBytes("/sdcard/fpga/cineraria_ulexite_top.rbf");
        if(rbfBuf == null) return;
        fpgaConfig(rbfBuf);
    }

    void fpgaConfig(byte[] rbfBuf) {
        byte[] wbuf = new byte[1];
        byte[] rbuf = new byte[1];

        ReadThreadStop();

        wbuf[0] = 0x3A;
        mFTDI.write(wbuf, 1);

        rbuf[0] = 0x00;
        mFTDI.read(rbuf, 1, 1000);

        if(DEBUG_SHOW) {
        	tvLog.append("read : " + toHexStr(rbuf, 1) + "\n");
        }

        if(rbuf[0] == 0x31) {
            Toast.makeText(this, "Start config error", Toast.LENGTH_LONG).show();
            return;
        } else if(rbuf[0] != 0x30) {
            // TODO 何も応答がない場合、0x31が買えるまで任意バイトを送信する
            // 0x31が返ってきたらやり直し
            Toast.makeText(this, "Illegal start config", Toast.LENGTH_LONG).show();
            return;
        }

        mFTDI.write(rbfBuf, rbfBuf.length);

        rbuf[0] = 0x00;
        mFTDI.read(rbuf, 1, 1000);

        if(DEBUG_SHOW) {
        	tvLog.append("read : " + toHexStr(rbuf, 1) + "\n");
        }

        if(rbuf[0] == 0x31) {
            Toast.makeText(this, "End config error", Toast.LENGTH_LONG).show();
            return;
        } else if(rbuf[0] != 0x32) {
            // TODO 何も応答がない場合、0x31が買えるまで任意バイトを送信する
            // 0x31が返ってきたらやり直し
            Toast.makeText(this, "Illegal end config", Toast.LENGTH_LONG).show();
            return;
        }

    }

    void writeFpga(byte[] buf, int size) {
        ArrayList<Byte> wbuf = new ArrayList<Byte>();
        for(byte b : buf) {
            if(b == 0x3A) {
                wbuf.add((byte)0x3D);
                wbuf.add((byte)0x1a);
            } else {
                wbuf.add(b);
            }
        }

        byte[] writeBuf = new byte[wbuf.size()];
        for(int i=0; i<wbuf.size(); i++) {
            writeBuf[i] = wbuf.get(i).byteValue();
        }

        mFTDI.write(writeBuf, writeBuf.length);
    }

    byte[] getRbfBytes(String rbfFilePath) {
        File rbfFile = new File(rbfFilePath);
        InputStream input = null;
        try {
            input = new FileInputStream(rbfFile);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "RBF File not found");
            return null;
        }

        int DEFAULT_BUFFER_SIZE = 1024 * 4;
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int n=0;
        int i;

        ArrayList<Byte> wbuf = new ArrayList<Byte>();

        try {
            while (-1 != (n = input.read(buffer))) {
                for(i=0; i<n; i++) {
                    wbuf.add(buffer[i]);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "RBF File IO Exception");
            return null;
        }

        try {
            input.close();
        } catch (IOException e) {
            Log.e(TAG, "RBF Closing file IO Exception");
            return null;
        }

        byte[] rbfBuf = new byte[wbuf.size()];
        for(i=0; i<wbuf.size(); i++) {
            rbfBuf[i] = wbuf.get(i).byteValue();
        }

        return rbfBuf;
    }

    public void onClickAvalonWrite(View v) {
        byte[] sendBytes;
        try {
            sendBytes = mAvalonSt.createWriteIncAddressPacket(etWriteAddr.getText().toString(), etWriteData.getText().toString());
            tvLog.append("S:("+sendBytes.length+") "+toHexStr(sendBytes, sendBytes.length)+"\n");
            writeFpga(sendBytes, sendBytes.length);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

        ReadThreadStart();

    }

    public void onClickAvalonRead(View v) {
        byte[] sendBytes;
        try {
            sendBytes = mAvalonSt.createReadIncAddressPacket(etReadAddr.getText().toString(), 4);
            tvLog.append("S:("+sendBytes.length+") "+toHexStr(sendBytes, sendBytes.length)+"\n");
            writeFpga(sendBytes, sendBytes.length);
        } catch (Exception e) {
            Toast.makeText(this, "Fail to create a packet", Toast.LENGTH_LONG).show();
            return;
        }

        ReadThreadStart();

    }

    private boolean mReadThreadRunning = false;
    private boolean mReadThreadStop = true;
    private static final int    READ_WAIT_MS    = 10;
    Handler mTvReadHandler  = new Handler();
    int     mReadSize=0;
    private static final int    MAX_READBUF_SIZE = 256;
    byte[] rbuf = new byte[MAX_READBUF_SIZE];

    /**
     * Starts read thread
     * @return true : successful, false :fail
     */
    public boolean ReadThreadStart() {
        if(!mReadThreadRunning) {
            mReadThreadRunning = true;
            mReadThreadStop = false;
            new Thread(mLoop).start();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Stops read thread
     * @return true : successful, false :fail
     */
    public boolean ReadThreadStop() {
        int count;
        if(mReadThreadRunning) {
            mReadThreadStop = true;
            count=0;
            while(mReadThreadRunning){
                if(count > 100) return false;   // 100 = 1sec
                try {
                    count++;
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            }
        }
        return true;
    }

    private Runnable mLoop = new Runnable() {
        @Override
        public void run() {

            if(mFTDI==null) return;

            for(;;){    // Read thread loop

                mReadSize = mFTDI.getQueueStatus();

                if(mReadSize > 0) {
                    if(mReadSize > MAX_READBUF_SIZE) mReadSize = MAX_READBUF_SIZE;

                    mReadSize = mFTDI.read(rbuf,mReadSize,READ_WAIT_MS); // You might want to set wait_ms.

                    mTvReadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if(tvLog != null) {
                                tvLog.append("R:("+mReadSize+") "+toHexStr(rbuf, mReadSize)+"\n");
                            }
                        }
                    });
                }

                if(mReadThreadStop) {
                    mReadThreadRunning = false;
                    return;
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                }

            }
        }
    };

    /**
     * Converts byte array to String
     * @param b byte array
     * @param length byte number to convert
     * @return hex String
     */
    private String toHexStr(byte[] b, int length) {
        String str="";
        for(int i=0; i<length; i++) {
            str += String.format("%02x ", b[i]);
        }
        return str;
    }

}
