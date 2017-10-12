package com.example.masters.finger;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.credenceid.biometrics.Biometrics;
import com.credenceid.biometrics.BiometricsActivity;

import org.kobjects.base64.Base64;
import org.ksoap2.SoapEnvelope;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;
import org.ksoap2.transport.HttpTransportSE;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends BiometricsActivity {


    ImageView image_view;
    TextView text_view;

    private static int close_cmd_counter = 0;
    private static int open_cmd_counter = 0;
    private Biometrics mBiometrics;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        image_view = (ImageView) findViewById(R.id.imageView);
        text_view = (TextView) findViewById(R.id.textView);
    }

    public static String fileName = "";
    public static byte[] fileContents = null;
    String device_id = "";
    Uri uri;

    public void onButtonClicked(View v) {

        switch (v.getId()) {
            case R.id.button:
                text_view.setText("Grab Fingerprint Start");
                image_view.setImageDrawable(null);
                grabFingerprint();
                break;
//            case R.id.button2:
//                text_view.setText("Smartcard Start");
////                openClose(false);
////                openClose(true);
//                break;
            case R.id.button3:
                text_view.setText("Camera Start");

                uploadPhoto();

                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES.toString());
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                fileName = timeStamp + ".jpg";

                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                File f = new File(dir, fileName);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
                uri = Uri.fromFile(f);
                startActivityForResult(intent, 111);


                break;
        }


    }

    @Override
    public void onFingerprintGrabbed(ResultCode result, Bitmap bitmap,
                                     byte[] iso, String filepath, String status) {
        if (status != null)
            text_view.setText(status);
        if (bitmap != null)
            image_view.setImageBitmap(bitmap);
        MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, "", "");

    }

    public void onCloseFingerprintReader(CloseReasonCode reasonCode) {
        text_view.setText("Sensor Closed. Reason=" + reasonCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == 111) {  // take photo

                int orientation = -1;
                ExifInterface exif;

                Uri selectedImage = uri;
                getContentResolver().notifyChange(selectedImage, null);
                Bitmap reducedSizeBitmap = getBitmap(uri.getPath()); // convert source picture to bitmap 500KB

                //+
                try {
                    exif = new ExifInterface(selectedImage.getPath());
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL);

                } catch (IOException e) {
                    e.printStackTrace();
                }

                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:   // 6
                        reducedSizeBitmap = rotateImage(reducedSizeBitmap, 90);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:  // 3
                        reducedSizeBitmap = rotateImage(reducedSizeBitmap, 180);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:  // 8
                        reducedSizeBitmap = rotateImage(reducedSizeBitmap, 270);
                        break;
                    default:   // 0
                        //reducedSizeBitmap = rotateImage(reducedSizeBitmap, 0);
                }
                //-

                ImageView imgView = (ImageView) findViewById(R.id.imageView);
                imgView.setImageBitmap(reducedSizeBitmap);
                galleryAddPic();
            }
        }


    }

    public static Bitmap rotateImage(Bitmap source, float angle) {  // rotate bitmap
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }

    public Bitmap getBitmap(String path) {            // return Bitmap from file path

        Uri uri = Uri.fromFile(new File(path));
        InputStream in = null;
        try {
            final int IMAGE_MAX_SIZE = 500000; // 500 KB
            in = getContentResolver().openInputStream(uri);

            // Decode image size
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, o);
            in.close();


            int scale = 1;
            while ((o.outWidth * o.outHeight) * (1 / Math.pow(scale, 2)) >
                    IMAGE_MAX_SIZE) {
                scale++;
            }
            //Log.d("", "scale = " + scale + ", orig-width: " + o.outWidth + ", orig-height: " + o.outHeight);

            Bitmap b = null;
            in = getContentResolver().openInputStream(uri);
            if (scale > 1) {
                scale--;
                // scale to max possible inSampleSize that still yields an image
                // larger than target
                o = new BitmapFactory.Options();
                o.inSampleSize = scale;
                b = BitmapFactory.decodeStream(in, null, o);

                // resize to desired dimensions
                int height = b.getHeight();
                int width = b.getWidth();
                //Log.d("", "1th scale operation dimenions - width: " + width + ", height: " + height);

                double y = Math.sqrt(IMAGE_MAX_SIZE
                        / (((double) width) / height));
                double x = (y / height) * width;

                Bitmap scaledBitmap = Bitmap.createScaledBitmap(b, (int) x,
                        (int) y, true);
                b.recycle();
                b = scaledBitmap;

                System.gc();
            } else {
                b = BitmapFactory.decodeStream(in);
            }
            in.close();

            //Log.d("", "bitmap size - width: " + b.getWidth() + ", height: " + b.getHeight());
            return b;
        } catch (IOException e) {
            //Log.e("", e.getMessage(), e);
            return null;
        }
    }

    private void galleryAddPic() {
        try {
            if (Build.VERSION.SDK_INT >= 9) {
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                File f1 = new File("file://" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES));
                Uri contentUri = Uri.fromFile(f1);
                mediaScanIntent.setData(contentUri);
                sendBroadcast(mediaScanIntent);
            } else {
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://" + Environment.getExternalStorageDirectory())));
            }
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES.toString());
            File f = new File(dir, fileName);
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f)));
        } catch (Exception e) {
            e.printStackTrace();
            //Toast.makeText(this,e.toString(),Toast.LENGTH_LONG).show();
            //DisplayToast(e.toString());
        }


    }

    private void uploadPhoto() {

        String fileNameR="";
        byte[] fileContentsR = null;

        fileNameR = MainActivity.fileName;
        fileContentsR = MainActivity.fileContents;

        String strResult = UploadPictureViaWebservice(fileNameR, fileContentsR);

        strResult = strResult + "#" + "XXXXXXXXXXXX" + "#" + "Unkonown" + "#" + "0/0";

        String _return = strResult.replaceAll("\\s", "");
//        if (_return == "false") {
//            Toast.makeText(ResultActivity.this, "Failed!!", Toast.LENGTH_LONG).show();
//        } else {
//            ;
//            String[] arr_str = strResult.split("#");
//
//            strID = arr_str[1];
//            strName = arr_str[2];
//            strScore = arr_str[3];

//            strResult = arr_str[0];

        byte[] imageAsBytes = android.util.Base64.decode(strResult.getBytes(), android.util.Base64.DEFAULT);
        ImageView image = (ImageView) findViewById(R.id.imageView);
        image.setImageBitmap(BitmapFactory.decodeByteArray(imageAsBytes, 0, imageAsBytes.length));

//            Toast.makeText(ResultActivity.this, "Successed!", Toast.LENGTH_LONG).show();

//        text_view = (TextView) findViewById(R.id.textView);
//        txtStudentID = (TextView) findViewById(R.id.txtStudentID);
//        txtStudentName = (TextView) findViewById(R.id.txtStudentName);
//        txtStudentScore = (TextView) findViewById(R.id.txtStudentScore);
//        txtStudentID.setText("   ID : " + strID);
//        txtStudentName.setText("   Name : " + strName);
//        txtStudentScore.setText("   Score : " + strScore);
//
//    }
//

        fileContentsR = null;
        fileContents = null;

    }

    public String UploadPictureViaWebservice(String filename, byte[] imgArr) {

        if (filename == "") {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            filename = device_id + "_" + timeStamp + ".jpg";
            fileName = filename;
        }

        String strResponse = "";
        String encodedImage = android.util.Base64.encodeToString(imgArr, android.util.Base64.DEFAULT);

        String URL = "http://203.151.213.80/ServiceAforge/Wacservice.asmx";
        String NAMESPACE = "http://tempuri.org/";
        String METHOD_NAME = "_MainProcessStringImage";
        String SOAP_ACTION = "http://tempuri.org/_MainProcessStringImage/";
        SoapObject request = new SoapObject(NAMESPACE, METHOD_NAME);

        /**** with parameter *****/
        PropertyInfo pi;
        pi = new PropertyInfo();
        pi.setName("filenameImage");
        pi.setValue(filename);
        pi.setType(String.class);
        request.addProperty(pi);

        pi = new PropertyInfo();
        pi.setName("StringBase64");
        pi.setValue(encodedImage);
        pi.setType(String.class);
        request.addProperty(pi);
        /*************************/

        SoapSerializationEnvelope envelope = new SoapSerializationEnvelope(SoapEnvelope.VER12);
        envelope.dotNet = true;
        envelope.setOutputSoapObject(request);
        HttpTransportSE androidHttpTransport = new HttpTransportSE(URL);
        androidHttpTransport.debug = true;
        try {
            androidHttpTransport.call(SOAP_ACTION, envelope);
            SoapObject response;
            response = (SoapObject) envelope.bodyIn;
            strResponse = response.getProperty(0).toString();
        } catch (Exception e) {
            //e.printStackTrace();
            strResponse = e.toString();
        }

        return strResponse;

    }
}