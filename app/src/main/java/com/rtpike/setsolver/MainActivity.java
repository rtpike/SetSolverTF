package com.rtpike.setsolver;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener;
import org.opencv.android.JavaCameraView;
import org.opencv.imgproc.Imgproc;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import java.util.List;


public class MainActivity extends AppCompatActivity implements CvCameraViewListener, GestureDetector.OnGestureListener {
    //View.OnTouchListener,
    private static final String TAG = ":MainActivity";

    private static final int INPUT_SIZE = 320; //350 after warp, reduced to 320 when white balanced
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;
    private static final String INPUT_NAME = "Mul:0";
    private static final String OUTPUT_NAME = "final_result";

    private static final String MODEL_FILE = "file:///android_asset/rounded_graph.pb";
    private static final String LABEL_FILE = "file:///android_asset/retrained_labels.txt";

    private static final boolean SAVE_PREVIEW_BITMAP = false;

    private static final boolean MAINTAIN_ASPECT = true;

    private static final android.util.Size DESIRED_PREVIEW_SIZE = new android.util.Size(640, 480);




    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private static final int WHAT_PROCESS_IMAGE = 0;

    private CameraBridgeViewBase mOpenCvCameraView;
    private processImage mProcessImage;
    private MenuItem mItemDebugCard;
    private MenuItem mItemDebugMode;
    private GestureDetectorCompat mDetector;
    private boolean camRunning = true;
    private int currentCard = 0;
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {

        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");

                    /* Now enable camera view to start receiving frames */
                    //mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                    mOpenCvCameraView.setCvCameraViewListener(MainActivity.this);
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Creating and setting view");
        setContentView(R.layout.activity_fullscreen);

        mVisible = true;
        //mControlsView = findViewById(R.id.fullscreen_content_controls);
        //mControlsView.setOnTouchListener(MainActivity.this);
        ////mContentView = findViewById(R.id.fullscreen_content);
        ////mContentView.setOnTouchListener(MainActivity.this);

        // Instantiate the gesture detector with the
        // application context and an implementation of
        // GestureDetector.OnGestureListener

        mDetector = new GestureDetectorCompat(this, this);

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        ////findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        mOpenCvCameraView = new JavaCameraView(this, -1);

        setContentView(mOpenCvCameraView);
        mOpenCvCameraView.setMaxFrameSize(800, 600); //frame size
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        //TODO: added tensorflow code to figure out number, fill and shape features
        // See code: tensorflow-for-poets-2\android\src\org\tensorflow\demo\ClassifierActivity.java

        Classifier classifier =
                TensorFlowImageClassifier.create(
                        getAssets(),
                        MODEL_FILE,
                        LABEL_FILE,
                        INPUT_SIZE,
                        IMAGE_MEAN,
                        IMAGE_STD,
                        INPUT_NAME,
                        OUTPUT_NAME);

        mProcessImage = new processImage(this.getBaseContext(),classifier);
        //FIXME mProcessImage.prepareNewGame();

    }

/*    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }*/

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        if (mContentView != null)
            mContentView.destroyDrawingCache();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //TODO: put into XML
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemDebugCard = menu.add("Inspect Cards");
        mItemDebugCard.setCheckable(true);
        mItemDebugMode = menu.add("Show Debug Info");
        mItemDebugMode.setCheckable(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "Menu Item selected " + item);
        if (item == mItemDebugMode) {
            /* Toggle Debug mode */
            if (mProcessImage != null) {
                mProcessImage.debug = !mItemDebugMode.isChecked();
                mItemDebugMode.setChecked(mProcessImage.debug);
            }
            //FIXME mProcessImage.prepareNewGame();
        } else if (item == mItemDebugCard) {
            //Toggle check box
            mItemDebugCard.setChecked(!mItemDebugCard.isChecked());

            if (mItemDebugCard.isChecked()) {
                camRunning = false;
                currentCard = 0;
                setContentView(R.layout.activity_fullscreen);
                showCard();
            } else {
                camRunning = true;
                setContentView(mOpenCvCameraView);
                mOpenCvCameraView.enableView();
            }
        }
        return true;
    }

    public void onCameraViewStarted(int width, int height) {
        camRunning = true;
    }

    public void onCameraViewStopped() {
        camRunning = false;
    }

    public boolean myTouchEvent(MotionEvent event) {

        if (mItemDebugCard.isChecked() && !camRunning) {
            showCard();
            return false;
        }

        if (camRunning) { //pause
            onPause();
            mOpenCvCameraView.disableView();
            camRunning = false;
        } else {
            onResume();
            mOpenCvCameraView.enableView();
            camRunning = true;
        }
        return false;

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        this.mDetector.onTouchEvent(event);
        // Be sure to call the superclass implementation
        return super.onTouchEvent(event);
    }

    public void showCard() {
        /* Get next card on touch */
        //imgView.setOnTouchListener(new OnTouchListener(){

        Card cardObj;
        List<Card> cards = mProcessImage.getCards();
        if (cards.size() <= 0) {
            return;
        } //no cards found
        if (findViewById(android.R.id.content).getId() != R.layout.activity_fullscreen) {
            setContentView(R.layout.activity_fullscreen);
        }

        ImageView imgView = (ImageView) findViewById(R.id.fullscreen_content);
        if (currentCard < 0) {
            currentCard = cards.size() + currentCard;
        } //negative currentCard
        cardObj = cards.get(currentCard % cards.size());
        Mat cardMat = cardObj.cardImg_markup;
        //Imgproc.cvtColor(cardObj.cardImg_markup, cardMat, Imgproc.COLOR_BGR2BGRA);

        Bitmap img = Bitmap.createBitmap(cardMat.cols(), cardMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(cardMat, img);
        imgView.setImageBitmap(img);
        currentCard = (currentCard + 1) % cards.size();

        //clear image (use either)
        //imgView.setImageResource(android.R.color.transparent);
        //imgView.setImageResource(0);
    }

    public void switchContentView(View v) {
        setContentView(R.layout.activity_fullscreen);
        setContentView(mOpenCvCameraView);
    }

    public Mat onCameraFrame(Mat inputFrame) {


        Handler mHandler = null;
        mHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {

            @Override
            public boolean handleMessage(Message msg) {
                if (msg.what == WHAT_PROCESS_IMAGE) {
                    threadPool.post(new Runnable() {
                        @Override
                        public void run() {
                            //do everything
                        }
                    });
                }
                return true;
            }
        });

        Imgproc.cvtColor(inputFrame, inputFrame, Imgproc.COLOR_RGBA2RGB);  //without this getRectSubPix will crash
        Mat image = mProcessImage.detectCards(inputFrame);
/*        if (mProcessImage.numCards() >= 3) {
            onPause();
            camRunning =false;
        }*/
        return image;
    }

/*
    // result Integer is passed here after
    // this method is run on main UI thread
    @Override
    protected void onPostExecute(Integer result) {
    }
*/

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return myTouchEvent(e);
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        //TODO: fix SWIPE_MIN_DISTANCE
        final int SWIPE_MIN_DISTANCE = 50;
        int nextOrPrev = 0;


        if (mItemDebugCard.isChecked() && !camRunning) {

            //swipe right
            if (e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE) {
                nextOrPrev = 0;
            } else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE) { //left
                nextOrPrev = -2;
            }
            currentCard = currentCard + nextOrPrev;


            showCard();
            return false;
        }
        return false;
    }
}




