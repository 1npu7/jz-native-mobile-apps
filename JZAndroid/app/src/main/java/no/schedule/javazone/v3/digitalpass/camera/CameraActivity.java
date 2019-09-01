package no.schedule.javazone.v3.digitalpass.camera;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import no.schedule.javazone.v3.R;
import no.schedule.javazone.v3.digitalpass.pass.PassFragment;
import no.schedule.javazone.v3.ui.BaseActivity;

import static no.schedule.javazone.v3.util.LogUtils.makeLogTag;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class CameraActivity extends BaseActivity {

    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 1;
    public static final int BARCODE_REQUEST = 0;
    public static final int PARTNER_SCAN = 1;

    private static final String TAG = makeLogTag(PassFragment.class);
    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;
    private int requestCode;

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        this.requestCode = intent.getIntExtra("requestCode", -1);
        Log.d("CameraActivity", "requestCode: " + this.requestCode);
        Log.d("startActivityForRes", "test");
        intent.putExtra("requestCode", requestCode);
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.requestCode = getIntent().getIntExtra("requestCode", -1);
        Log.d("CameraActivity", "requestCode: " + this.requestCode);
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != this.getPackageManager().PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_CAMERA);
        }else{
            setupCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        switch(requestCode){
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    setupCamera();
                } else {
                    finish();
                }
            }
        }
    }

    private void setupCamera(){
        setContentView(R.layout.activity_camera);

        preview = findViewById(R.id.firePreview);
        graphicOverlay = findViewById(R.id.fireFaceOverlay);
        if (cameraSource == null) {
            cameraSource = new CameraSource(this, graphicOverlay);
        }
        cameraSource.setMachineLearningFrameProcessor(new BarcodeScanningProcessor(this, this.requestCode));
        try {
            Log.d(TAG, "onClick: start camera");
            preview.start(cameraSource, graphicOverlay);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onPartnerScan(String code){
        Intent resultIntent = new Intent();
        resultIntent.putExtra("requestCode", requestCode);

        String key = "", name = "";

        try{
            JSONObject stampQrCode = new JSONObject(code);
            key = stampQrCode.getString("Key");
            name = stampQrCode.getString("Name");
        }
        catch (JSONException e){
            Log.d("StampQrCode", e.getMessage());

            e.printStackTrace();
        }

        Log.d("StampQrCode", key);
        Log.d("StampQrCode", name);

        resultIntent.putExtra("code", key);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    public void onQrScanned(FirebaseVisionBarcode.ContactInfo contactInfo){
        Intent resultIntent = new Intent();

        if(contactInfo == null || contactInfo.getEmails().isEmpty() || contactInfo.getName() == null){
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        resultIntent.putExtra("requestCode", requestCode);
        resultIntent.putExtra("name", contactInfo.getName().getFormattedName());
        resultIntent.putExtra("email", contactInfo.getEmails().get(0).getAddress());

        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
    }
}
