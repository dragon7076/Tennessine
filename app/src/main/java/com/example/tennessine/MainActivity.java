package com.example.tennessine;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity{

    private TextView textout;
    private static final int FILE_INTENT_CODE = 10;
    private static final int CAMERA_INTENT_CODE = 11;
    private boolean captionbool = true;
    private boolean yolobool = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // get permissions
        verifyStoragePermissions(this);

        // get elements
        textout = findViewById(R.id.textView);
        textout.setText("Awaiting action");
        RadioButton yoloButton = findViewById(R.id.yoloButton);
        RadioButton captionButton = findViewById(R.id.captionButton);
        Button photoButton = findViewById(R.id.photoButton);
        Button uploadButton = findViewById(R.id.uploadButton);

        // set button actions
        uploadButton.setOnClickListener(
                view -> dispatchOpenPictureIntent(view)
        );
        photoButton.setOnClickListener(
                view -> dispatchTakePictureIntent(view)
        );
        yoloButton.setOnClickListener(v -> {
            yolobool = true;
            captionbool = false;
        });
        captionButton.setOnClickListener(v -> {
            captionbool = true;
            yolobool = false;
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String path;
        if (data != null) {
            switch (requestCode) {
                case FILE_INTENT_CODE:
                    path = data.getData().getPath().split(":")[1]; // so, so gross. im so sorry
                    textout.setText(path);
                    uploadImage(path);
                    break;
                case CAMERA_INTENT_CODE:
                    uploadImage(currentPhotoPath);
                    break;
            }
        }
    }

    String currentPhotoPath;

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void dispatchTakePictureIntent(View view) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Create the File where the photo should go
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            basicSnackbarPrint(view, "Could create image file");
        }
        // Continue only if the File was successfully created
        if (photoFile != null) {
            Uri photoURI = FileProvider.getUriForFile(this,
                    "com.example.android.fileprovider",
                    photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            startActivityForResult(takePictureIntent, CAMERA_INTENT_CODE);
        }
    }

    private void dispatchOpenPictureIntent(View view) {
        Intent fileIntent;
        fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        fileIntent.setType("*/*");
        try {
            startActivityForResult(fileIntent, FILE_INTENT_CODE);
        } catch (ActivityNotFoundException e) {
            basicSnackbarPrint(view, "Could not open file explorer");
        }
    }

    // copied from https://stackoverflow.com/questions/8854359/exception-open-failed-eacces-permission-denied-on-android
    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * Checks if the app has permission to write to device storage
     * <p>
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    private void basicSnackbarPrint(View view, String s) {
        Snackbar.make(view, s, Snackbar.LENGTH_LONG).setAction("Action", null).show();
    }

    // these should probably be changeable in a settings page?
    private static final String captionURL = "https://astatine.vulpinecitrus.info/captioning";
    private static final String yoloURL = "https://astatine.vulpinecitrus.info/yolo";

    private void uploadImage(String imagePath) {
        // shamelessly copied from
        // https://stackoverflow.com/questions/3324717/sending-http-post-request-in-java
        // running in a thread to avoid android.os.networkonmainthreadexception
        Thread thread = new Thread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void run() {
                try {
                    textout.setText("Connecting to server");
                    URL url = null;
                    if (captionbool)
                        url = new URL(captionURL);
                    else if (yolobool)
                        url = new URL(yoloURL);
                    else
                        throw new java.net.MalformedURLException("idfk");
                    URLConnection con = url.openConnection();

                    textout.setText("Connected, sending image");
                    HttpURLConnection http = (HttpURLConnection) con;
                    http.setRequestMethod("POST");
                    http.setDoOutput(true);

                    String boundary = UUID.randomUUID().toString();
                    byte[] boundaryBytes =
                            ("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8);
                    byte[] finishBoundaryBytes =
                            ("--" + boundary + "--").getBytes(StandardCharsets.UTF_8);
                    http.setRequestProperty("Content-Type",
                            "multipart/form-data; charset=UTF-8; boundary=" + boundary);

                    // Enable streaming mode with default settings
                    http.setChunkedStreamingMode(0);

                    // Send our fields:
                    OutputStream out = http.getOutputStream();
                    // Send our header (thx Algoman)
                    out.write(boundaryBytes);

                    // Send our file
                    InputStream file = new FileInputStream(imagePath);
                    String temp[] = imagePath.split("/");
                    String fileName = temp[temp.length - 1];
                    sendFile(out, "image", file, fileName);

                    // Send a seperator
                    out.write(boundaryBytes);

                    // Send our second field
                    sendField(out, "send_result", "false");

                    // Finish the request
                    out.write(finishBoundaryBytes);

                    textout.setText("Sent, awaiting server response");

                    // Do something with http.getInputStream()
                    String result = new BufferedReader(new InputStreamReader(http.getInputStream()))
                            .lines().collect(Collectors.joining("\n"));

                    String[] lines = result.split("\\r?\\n");
                    textout.setText("Description:\n" + lines[3]); // this is gross, sorry

                } catch (java.net.MalformedURLException e) {
                    textout.setText("Malformed URL exception");
                    e.printStackTrace();
                } catch (ProtocolException e) {
                    textout.setText("Protocol exception");
                    e.printStackTrace();
                } catch (IOException e) {
                    textout.setText("IO exception");
                    e.printStackTrace();
                } catch (Exception e) {
                    textout.setText("unexpected exception: " + e.getCause() + "\nplease try again");
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void sendFile(OutputStream out, String name, InputStream in, String fileName) throws IOException {
        String o = "Content-Disposition: form-data; name=\"" + URLEncoder.encode(name, "UTF-8")
                + "\"; filename=\"" + URLEncoder.encode(fileName, "UTF-8") + "\"\r\n\r\n";
        out.write(o.getBytes(StandardCharsets.UTF_8));
        byte[] buffer = new byte[2048];
        for (int n = 0; n >= 0; n = in.read(buffer))
            out.write(buffer, 0, n);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void sendField(OutputStream out, String name, String field) throws IOException {
        String o = "Content-Disposition: form-data; name=\""
                + URLEncoder.encode(name, "UTF-8") + "\"\r\n\r\n";
        out.write(o.getBytes(StandardCharsets.UTF_8));
        out.write(URLEncoder.encode(field, "UTF-8").getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}