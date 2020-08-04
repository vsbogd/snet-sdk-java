package com.example.snetdemoproxy;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.protobuf.ByteString;

import java.io.IOException;

import io.grpc.ManagedChannel;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.singularitynet.service.semanticsegmentation.Segmentation;
import io.singularitynet.service.semanticsegmentation.SemanticSegmentationGrpc;

public class MainActivity extends AppCompatActivity
{
    private final String TAG = "ImageSegmentationDemo";

    private Button btn_UploadImageInput;
    private Button btn_RunImageSegmentation;

    private ImageView imv_Input;

    private RelativeLayout loadingPanel;
    private TextView textViewProgress;
    private TextView textViewResponseTime;

    private Bitmap decodedBitmap = null;

    private final int PROGRESS_WAITING_FOR_SERIVCE_RESPONSE = 2;
    private final int PROGRESS_DECODING_SERIVCE_RESPONSE = 3;
    private final int PROGRESS_LOADING_IMAGE = 4;
    private final int PROGRESS_FINISHED = 5;

    private boolean isInputImageUploaded = false;

    private final int maxImageHeight = 1024;
    private final int maxImageWidth = 1024;

    private long serviceResponseTime = 0;

    private PermissionRequester permissionRequester;

    Uri imageInputUri = null;

    private SemanticSegmentationGrpc.SemanticSegmentationBlockingStub stub;
    private ManagedChannel channel;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setTitle("Image Segmentation Demo");

        btn_UploadImageInput = findViewById(R.id.btn_uploadImageForSegmentation);
        btn_RunImageSegmentation = findViewById(R.id.btn_runImageSegmentation);
        btn_RunImageSegmentation.setEnabled(false);

        imv_Input = findViewById(R.id.imageViewInput);

        loadingPanel = findViewById(R.id.loadingPanel);
        loadingPanel.setVisibility(View.INVISIBLE);

        textViewProgress = findViewById(R.id.textViewProgress);
        textViewProgress.setText("");
        textViewProgress.setVisibility(View.INVISIBLE);

        textViewResponseTime = findViewById(R.id.textViewResponseTime);
        textViewResponseTime.setText("Service response time (ms):");
        textViewResponseTime.setVisibility(View.INVISIBLE);


        permissionRequester = new PermissionRequester(this, new String[]{
                Manifest.permission.INTERNET,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
        }, this::initApp);
        permissionRequester.checkAndRequestPermissions();

    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    )
    {
        permissionRequester.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void disableActivityGUI()
    {
        textViewProgress.setVisibility(View.VISIBLE);
        loadingPanel.setVisibility(View.VISIBLE);

        btn_UploadImageInput.setEnabled(false);
        btn_RunImageSegmentation.setEnabled(false);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    private void enableActivityGUI()
    {
        loadingPanel.setVisibility(View.INVISIBLE);
        textViewProgress.setVisibility(View.INVISIBLE);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

        btn_UploadImageInput.setEnabled(true);

        if (isInputImageUploaded) {
            btn_RunImageSegmentation.setEnabled(true);
        }
    }

    protected void initApp()
    {
        new OpenServiceChannelTask().execute();
    }

    private void openProxyServiceChannel()
    {
        channel = OkHttpChannelBuilder
                .forAddress("sound-spleeter.singularitylab.io", 20010)
           .useTransportSecurity()
            .build();
        stub = SemanticSegmentationGrpc.newBlockingStub(channel);
    }

    private class OpenServiceChannelTask extends AsyncTask<Object, Object, Object>
    {
        private boolean isExceptionCaught = false;
        private String sError;

        protected void onPreExecute()
        {
            super.onPreExecute();
            textViewProgress.setText("Opening service channel");

            disableActivityGUI();
        }

        protected Object doInBackground(Object... param)
        {
            try
            {
                openProxyServiceChannel();
            }
            catch (Exception e)
            {
                Log.e(TAG, "Client connection error", e);
                sError = e.getMessage();
                isExceptionCaught = true;
            }
            return null;
        }

        protected void onPostExecute(Object obj)
        {
            if (isExceptionCaught)
            {
                isExceptionCaught = false;
                newAlertDialogBuilder(MainActivity.this)
                        .setTitle("Connection error")
                        .setMessage(sError)
                        .show();
            }
            else {
                enableActivityGUI();
            }
        }
    }
    private static AlertDialog.Builder newAlertDialogBuilder(Context context)
    {
        return new AlertDialog.Builder(context)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert);
    }

    public void sendUploadInputImageMessage(View view)
    {
        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        fileIntent.setType("image/*");
        startActivityForResult(fileIntent, 1);

        textViewResponseTime.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK)
        {
            isInputImageUploaded = true;
            Uri uri = data.getData();

            if (uri != null)
            {
                if(!ImageUtils.isUriReadable(uri, this))
                {
                    newAlertDialogBuilder(this)
                            .setTitle("ERROR")
                            .setMessage("Can't find file " + uri.getPath())
                            .show();

                    return;
                }
            }

            imageInputUri = uri;

            loadImageFromFileToImageView(imv_Input, imageInputUri);

            btn_RunImageSegmentation.setEnabled(true);
        }
    }

    private void loadImageFromFileToImageView(ImageView imgView, Uri fileURI)
    {
        Glide.with(this)
                .clear(imgView);

        Glide.with(this)
                .load(fileURI)
                .fitCenter()
                .into(imgView);
    }

    private class CallingServiceTask extends AsyncTask<Object, Integer, Object>
    {
        private boolean isExceptionCaught = false;
        private String errorMessage = "";

        protected void onPreExecute()
        {
            super.onPreExecute();

            disableActivityGUI();

        }

        protected Void doInBackground(Object... param)
        {
            publishProgress(new Integer(PROGRESS_LOADING_IMAGE));
            Bitmap bitmap;
            try
            {
                bitmap = ImageUtils.handleSamplingAndRotationBitmap(MainActivity.this, imageInputUri,
                        maxImageWidth, maxImageHeight);
            }
            catch (IOException e)
            {
                Log.e(TAG, "Exception on loading bitmap", e);

                errorMessage = e.toString();
                isExceptionCaught = true;

                return null;
            }

            byte[] bytesInput = ImageUtils.BitmapToJPEGByteArray(bitmap);
            publishProgress(new Integer(PROGRESS_WAITING_FOR_SERIVCE_RESPONSE));

            long startTime = System.nanoTime();
            Segmentation.Result response;

            try
            {
                response = callSegmentationService(bytesInput);
            }
            catch (Exception e)
            {
                Log.e(TAG, "Exception on service call", e);

                errorMessage = e.toString();
                isExceptionCaught = true;

                return null;
            }

            serviceResponseTime = System.nanoTime() - startTime;

            publishProgress(new Integer(PROGRESS_DECODING_SERIVCE_RESPONSE));

            Segmentation.Image dbgImage = response.getDebugImg();
            byte[] decodedBytes = dbgImage.getContent().toByteArray();

            MainActivity.this.decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            publishProgress(PROGRESS_FINISHED);

            return null;
        }

        protected void onProgressUpdate(Integer... progress)
        {
            int v = progress[0].intValue();
            switch (v)
            {
                case PROGRESS_WAITING_FOR_SERIVCE_RESPONSE:
                    textViewProgress.setVisibility(View.VISIBLE);
                    textViewProgress.setText("Waiting for response");
                    break;
                case PROGRESS_DECODING_SERIVCE_RESPONSE:
                    textViewProgress.setText("Decoding response");
                    break;
                case PROGRESS_LOADING_IMAGE:
                    textViewProgress.setText("Loading Image");
                    break;
                case PROGRESS_FINISHED:
                    textViewProgress.setVisibility(View.INVISIBLE);
                    break;
            }

        }

        protected void onPostExecute(Object obj)
        {
            if (isExceptionCaught)
            {
                isExceptionCaught = false;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("ERROR")
                        .setMessage(errorMessage)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })

                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            }
            else
            {
                imv_Input.setImageBitmap(MainActivity.this.decodedBitmap);

                serviceResponseTime /= 1e6;
                textViewResponseTime.setText("Service response time (ms): " + String.valueOf(serviceResponseTime));
                textViewResponseTime.setVisibility(View.VISIBLE);

                isInputImageUploaded = false;

            }

            enableActivityGUI();

        }
    }

    private Segmentation.Result callSegmentationService(byte[] bytesInput)
    {
        String mimeType = "image/jpeg";

        Segmentation.Request request = Segmentation.Request.newBuilder()
                .setImg(Segmentation.Image.newBuilder()
                        .setContent(ByteString.copyFrom(bytesInput))
                        .setMimetype(mimeType)
                        .build()
                )
                .setVisualise(true)
                .build();

        return stub.segment(request);
    }

    public void sendRunImageSegmentationMessage(View view)
    {
        if(this.isInputImageUploaded)
        {
            new CallingServiceTask().execute();
            isInputImageUploaded = false;
        }
    }
}