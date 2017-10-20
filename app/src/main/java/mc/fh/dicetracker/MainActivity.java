package mc.fh.dicetracker;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.flurgle.camerakit.CameraListener;
import com.flurgle.camerakit.CameraView;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int INPUT_SIZE = 299;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128;
    private static final String INPUT_NAME = "Mul";
    private static final String OUTPUT_NAME = "final_result";


    private static final String MODEL_FILE = "file:///android_asset/retrained_graph_optimized.pb";
    private static final String LABEL_FILE =
            "file:///android_asset/retrained_labels.txt";

    //static {
    //    System.loadLibrary("tensorflow_inference");
    //}


    private Classifier mClassifier;
    private Executor mExecuter = Executors.newSingleThreadExecutor();
    private CameraView mCameraView;
    private ImageView mImgResult;
    private TextView mTxtResult, mTxtIPAddress;

    private Button mBtnDetectImage;
    private Button mBtnSendToRPTools;

    private String mServerIP ="10.0.0.10";
    private String currentDieValue = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Initialize UI
        mCameraView = (CameraView)findViewById(R.id.cameraView);
        mImgResult = (ImageView)findViewById(R.id.imgResult);

        mTxtResult = (TextView)findViewById(R.id.txtResults);
        mTxtIPAddress = (EditText)findViewById(R.id.txtIPAddress);

        mBtnDetectImage = (Button)findViewById(R.id.btnDetectImage);
        mBtnSendToRPTools = (Button)findViewById(R.id.btnSendToRPTools);

        mCameraView.setCameraListener(new CameraListener() {
            @Override
            public void onPictureTaken(byte[] jpeg) {
                super.onPictureTaken(jpeg);

                Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);

                bitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);

                mImgResult.setImageBitmap(bitmap);

                final List<Classifier.Recognition> results = mClassifier.recognizeImage(bitmap);

                //Toast.makeText(MainActivity.this, results.get(0).toString(), Toast.LENGTH_SHORT).show();
                String[] resString = results.get(0).toString().split(" ");
                Log.d("MainActivity", resString[1] + ";" + resString[2]);
                Toast.makeText(MainActivity.this,  resString[1] + ";" + resString[2], Toast.LENGTH_SHORT).show();

                currentDieValue = resString[1]+";"+resString[2];
                mTxtResult.setText(results.toString());
            }
        });

        mBtnDetectImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraView.captureImage();
            }
        });

        mBtnSendToRPTools.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mServerIP = mTxtIPAddress.getText().toString();
                MessageTask sendMsgTask = new MessageTask();
                sendMsgTask.execute();
            }
        });

        initTensorFlowAndLoadModel();
    }

    /**
     * Used for sending the dice roll result to MapTool via Socket.
     */
   private class MessageTask extends AsyncTask<Void, Void, Void> {

        private boolean sendingSuccessful;

       @Override
       protected Void doInBackground(Void... voids) {
           try {
               Socket clientSocket = new Socket(mServerIP, 8080);
               PrintWriter pWriter = new PrintWriter(clientSocket.getOutputStream(), true);
               //pWriter.write("Hello from the otter slide!");
               pWriter.write(currentDieValue);

               sendingSuccessful = true;

               pWriter.flush();
               pWriter.close();
               clientSocket.close();

           } catch (IOException e) {
               e.printStackTrace();
               sendingSuccessful = false;
           }

           return null;
       }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (sendingSuccessful) {
                Toast.makeText(MainActivity.this, "Value sent to MapTool!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Error sending to MapTool!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraView.start();
    }

    @Override
    protected void onPause() {
        mCameraView.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecuter.execute(new Runnable() {
            @Override
            public void run() {
                mClassifier.close();
            }
        });
    }

    private void initTensorFlowAndLoadModel() {
        mExecuter.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mClassifier = TensorFlowImageClassifier.create(
                            getAssets(),
                            MODEL_FILE,
                            LABEL_FILE,
                            INPUT_SIZE,
                            IMAGE_MEAN,
                            IMAGE_STD,
                            INPUT_NAME,
                            OUTPUT_NAME);
                    makeButtonVisible();
                } catch (final Exception e) {
                    throw new RuntimeException("Error initializing TensorFlow!", e);
                }
            }
        });
    }

    private void makeButtonVisible() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mBtnDetectImage.setVisibility(View.VISIBLE);
            }
        });
    }

}
