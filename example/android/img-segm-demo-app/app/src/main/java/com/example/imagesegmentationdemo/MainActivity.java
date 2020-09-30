package com.example.imagesegmentationdemo;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.bumptech.glide.Glide;

public class MainActivity extends AppCompatActivity
{
    private Button btnOpenImage;
    private Button btnCallService;

    private ImageView imageView;

    private ProgressBar progressBar;

    private boolean isInputImageUploaded = false;

    private PermissionRequester permissionRequester;

    private HandlerMainActivity handler;

    private SNETServiceHelper serviceHelper;

    private Uri imageUri;

    static final int REQUEST_CODE_OPEN_IMAGE = 0;

    public final int maxImageHeight = 1024;
    public final int maxImageWidth = 1024;

    public static AlertDialog.Builder newAlertDialogBuilder(Context context) {
        return new AlertDialog.Builder(context)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert);
    }

    public void msgShowException(Exception e)
    {
        msgShowError(e.getMessage());
    }

    public void msgShowError(String message)
    {
        handler.sendMessage(handler.obtainMessage(
                HandlerMainActivity.MSG_SHOW_ERROR, message));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnOpenImage = findViewById(R.id.btnOpenImage);
        btnCallService= findViewById(R.id.btnCallService);
        btnCallService.setEnabled(false);

        imageView = findViewById(R.id.imageView);

        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.INVISIBLE);

        handler = new HandlerMainActivity(this);
        serviceHelper = new SNETServiceHelper(handler);

        this.permissionRequester = new PermissionRequester(this, new String[]{
                Manifest.permission.INTERNET,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, this::initialize);

        checkPermissionsAndInitialize();
    }

    public void enableActivityGUI()
    {
        progressBar.setVisibility(View.INVISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    public void disableActivityGUI()
    {
        progressBar.setVisibility(View.VISIBLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }
    public void setImageBitmap(Bitmap bm)
    {
        imageView.setImageBitmap(bm);
    }

    public void checkPermissionsAndInitialize()
    {
        permissionRequester.checkAndRequestPermissions();
    }

    public void initialize()
    {
        openProxyServiceChannelAsync();
    }

    public void openProxyServiceChannelAsync()
    {
        serviceHelper.openProxyServiceChannelAsync();
    }

    public void runImageSegmentationService(View view)
    {
        if(serviceHelper != null)
            serviceHelper.callServiceAsync();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK)
        {
            isInputImageUploaded = true;
            assert data != null;
            imageUri = data.getData();
            loadImageFromFileToImageView(imageView, imageUri);

            btnCallService.setEnabled(true);
        }
    }

    public void msgOpenImage(View view)
    {
        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        fileIntent.setType("image/*");
        startActivityForResult(fileIntent, REQUEST_CODE_OPEN_IMAGE);
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

    public Uri getImageUri()
    {
        return this.imageUri;
    }

    @Override
    protected void onDestroy()
    {
        serviceHelper.closeServiceChannel();
        super.onDestroy();
    }
}