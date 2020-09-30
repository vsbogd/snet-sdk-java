package com.example.imagesegmentationdemo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

public class SNETServiceHelper
{
    private HandlerMainActivity handlerMain;
    private ManagedChannel channel = null;

    private Thread threadServiceInit;
    private Thread threadServiceCalling;

    public SNETServiceHelper(HandlerMainActivity handlerMain)
    {
        this.handlerMain = handlerMain;
    }

    public void openProxyServiceChannelAsync()
    {
        threadServiceInit = new Thread(new Runnable()
        {
            public void run()
            {
                handlerMain.sendEmptyMessage(HandlerMainActivity.MSG_DISABLE_ACTIVITY_GUI);
                try
                {

                }
                catch (Exception e)
                {
                    showGeneralError(e);
                }
                handlerMain.sendEmptyMessage(HandlerMainActivity.MSG_ENABLE_ACTIVITY_GUI);
            }
        });

        threadServiceInit.start();
    }

    public void closeServiceChannel()
    {
        interruptServiceInit();
        interruptServiceCall();
        new Thread(new Runnable()
        {
            public void run()
            {
                if (channel != null)
                {
                    try
                    {
                        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
                    }
                    catch (InterruptedException e)
                    {
                        Log.e("CloseServiceChannelTask", "Closing channel error", e);
                    }
                }
            }
        }).start();
    }

    public void callServiceAsync()
    {
        threadServiceCalling = new Thread(new Runnable()
        {
            public void run()
            {
                handlerMain.sendEmptyMessage(HandlerMainActivity.MSG_DISABLE_ACTIVITY_GUI);

                /*
                *    PREPARE SERVICE INPUT DATA
                */
                byte[] data = new byte[0];


                /*
                *   CALL SINGULARITYNET SERVICE
                * */
                byte[] bytesInput = data;


                /*
                *   DECODE SERVICE RESPONSE
                 */
                handlerMain.sendEmptyMessage(HandlerMainActivity.MSG_ENABLE_ACTIVITY_GUI);

            }
        });

        threadServiceCalling.start();

    }

    private void handleGrpcStatusException(StatusRuntimeException e) {
        Status status = e.getStatus();
        switch (status.getCode()) {
            case RESOURCE_EXHAUSTED:
            case DEADLINE_EXCEEDED:
                showHighLoadMessage();
                break;
            case UNAVAILABLE:
                showUnavailableMessage();
                break;
            default:
                showGeneralError(e);
                break;
        }
    }

    private void showGeneralError(Exception e)
    {
        handlerMain.sendMessage(handlerMain.obtainMessage(
                HandlerMainActivity.MSG_SHOW_ERROR, e.getMessage()));
    }

    private void showUnavailableMessage()
    {
        String msg = "Service is temporary unavailable, please try again later";
        handlerMain.sendMessage(handlerMain.obtainMessage(
                HandlerMainActivity.MSG_SHOW_ERROR, msg));
    }

    private void showHighLoadMessage()
    {
        String msg = "Service is under high load, please try again later";
        handlerMain.sendMessage(handlerMain.obtainMessage(
                HandlerMainActivity.MSG_SHOW_ERROR, msg));
    }

    private void interruptServiceCall()
    {
        if(null != threadServiceCalling)
        {
            threadServiceCalling.interrupt();
        }
    }

    private void interruptServiceInit()
    {
        if(null != threadServiceInit)
        {
            threadServiceInit.interrupt();
        }
    }

}

