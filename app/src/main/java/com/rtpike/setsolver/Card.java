package com.rtpike.setsolver;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;

import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;

import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.core.Core.*;

import android.os.Handler;
import android.os.HandlerThread;


import java.util.List;
import java.util.Vector;
//import org.tensorflow.demo.OverlayView.DrawCallback;
//import org.tensorflow.demo.env.BorderedText;
//import org.tensorflow.demo.env.ImageUtils;
//import org.tensorflow.demo.env.Logger;

/**
 * Card Class for the Set cards
 * Created by rtpike on 1/19/2016.
 */



public class Card implements Runnable {
    static final boolean debug = true;
    private static final String TAG = "Card";
    public String cardName = null;
    public Thread thread = null;

    private Handler handler;
    private HandlerThread handlerThread;

    /*FEATURES = {"symbol": ["oval", "squiggle", "diamond"],
        "color": ["red", "green", "purple"],
        "amount": [1, 2, 3],
        "shading": ["solid", "open", "striped"]
    }*/

    public Mat cardImg;
    public Mat cardImg_markup = new Mat();
    public Mat cardThreshold = new Mat();
    public Mat cardHSV = new Mat();

    public MatOfPoint2f warpBox;
    public Mat parrentImage;
    public int number = -1; //number of shapes
    public colorEnum color = colorEnum.INVALID;  //red=0,green=1,purple=2
    public shadeEnum shade = shadeEnum.INVALID;  //0=empty, 1=lines, 2=solid
    public shapeEnum shape = shapeEnum.INVALID;  //"oval", "squiggle", "diamond"
    public Point[] corners; //From full parent image




    private static Classifier classifier;


    Card() {
    }


    Card(Context c, Mat cardImg) {
        int cropSize = 15;  //crop off 15 pixels per side
        this.cardImg = cardImg.submat(cropSize, cardImg.rows() - cropSize, cropSize, cardImg.cols() - cropSize); //crop off the edges


    }

    Card(Context c, Mat cardImg,Classifier classifier) {
        int cropSize = 15;  //crop off 15 pixels per side
        this.cardImg = cardImg.submat(cropSize, cardImg.rows() - cropSize, cropSize, cardImg.cols() - cropSize); //crop off the edges

        this.classifier = classifier;

/*        classifier = TensorFlowImageClassifier.create(
                c.getAssets(),
                MODEL_FILE,
                LABEL_FILE,
                INPUT_SIZE,
                IMAGE_MEAN,
                IMAGE_STD,
                INPUT_NAME,
                OUTPUT_NAME);
*/

    }


    /* preload warpBox and input image. Use with the runnable */
    Card(MatOfPoint2f warpBox, Mat in) {
        this.warpBox = warpBox;
        this.parrentImage = warpBox;
    }

    /* Rotate and crop the given image */
    public void processCardWarp(MatOfPoint2f warpBox, Mat in) {

        Mat cropped = new Mat();
        Point points[] = new Point[4];
        points[0] = new Point(0, 0);
        points[1] = new Point(350, 0);
        points[2] = new Point(350, 350);
        points[3] = new Point(0, 350);
        MatOfPoint2f destWarp = new MatOfPoint2f(points);

        Mat transform = Imgproc.getPerspectiveTransform(warpBox, destWarp);
        Imgproc.warpPerspective(in, cropped, transform, new Size(350, 350), Imgproc.INTER_NEAREST);

        int cropSize = 15;  //crop of 15 pixels per side
        this.cardImg = cropped.submat(cropSize, cropped.rows() - cropSize, cropSize, cropped.cols() - cropSize); //crop off the edges
        processCard();
    }

    public void processCard() {


        if (debug) {
            Log.d(TAG, "Card name: " + cardName + " vvvvvvvvvvvvvvvvvvvvvv");
        }
        //cardImg_markup = cardImg;
        detectColor();
        detectShape();

        //debug prints
        if (debug) {

            cardImg_markup.width();
            cardImg_markup.height();
            Point center = new Point(cardImg_markup.width() / 10, cardImg_markup.height() / 8);
            Imgproc.putText(cardImg_markup, decodeCard(), center, Core.FONT_HERSHEY_PLAIN, 2, new Scalar(70, 70, 70));
            //cardImg_markup = cardThreshold;  //debug
            Log.d(TAG, "Card Info: " + decodeCard() + " ^^^^^^^^^^^^^^^^^^^^^^^");

        }

    }

    /* Detect the shape and fill of the card */
    private void detectShape() {

        //Mat gray = new Mat();
        //Imgproc.cvtColor(cardImg_markup, gray, Imgproc.COLOR_RGB2GRAY);


        Bitmap bmp = Bitmap.createBitmap(cardImg_markup.cols(), cardImg_markup.rows(), Bitmap.Config.ARGB_8888);
        List<Classifier.Recognition> results = classifier.recognizeImage(bmp);

/*      public Recognition(
            final String id, final String title, final Float confidence, final RectF location) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
        }*/
        if (debug) {
            for (Classifier.Recognition result: results) {
                Log.d(TAG, "    " +  result);
            }
        }
        endodeCard(results.get(0)); //sets number, shade and shape

/*        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        //final long startTime = SystemClock.uptimeMillis();
                        final Bitmap bmp = Bitmap.createBitmap(cardImg_markup.cols(), cardImg_markup.rows(), Bitmap.Config.ARGB_8888);
                        final List<Classifier.Recognition> results = classifier.recognizeImage(bmp);
                        //lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        //cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        //resultsView.setResults(results);
                        //requestRender();
                        //computing = false;
                    }
                });*/

        Trace.endSection();
    }


    private void detectColor() {

        Mat sampleMat = null;
        //Mat blur = new Mat();

        //  ----------  White Balance ---------
        //sample "white" pixels at image at corners
        //Imgproc.medianBlur(cardImg, cardImg, 5);
        double[] whitePixel = new double[3];
        //whitePixel = cardImg.get(15, 15);
        sampleMat = cardImg.submat(0, 5, 0, 5);
        whitePixel = mean(sampleMat).val;

        if (debug) {
            Log.d(TAG, "    White sample: " + String.format("[%.0f, %.0f, %.0f]", whitePixel[0], whitePixel[1], whitePixel[2]));
        }

         double[] transMatrix = {255 / whitePixel[0], 0, 0, //Red
                                0, 255 / whitePixel[1], 0,  //Green
                                0, 0, 255 / whitePixel[2]}; //Blue

        Mat whiteCorrect = new Mat(3, 3, CvType.CV_64FC1);
        whiteCorrect.put(0, 0, transMatrix); //FIXME: change to setTo for speed
        transform(cardImg, cardImg_markup, whiteCorrect); //FIXME: try split/merge instead

        //TODO: try using cv::xphoto::SimpleWB Class Reference

/*        double[] transMatrix = {256 / whitePixel[0], //Red
                 256 / whitePixel[1],  //Green
                 256 / whitePixel[2]}; //Blue
        colorTransform(cardImg,transMatrix,cardImg_markup);*/
        //  ---------- End White Balance ---------

        Imgproc.cvtColor(cardImg_markup, cardHSV, Imgproc.COLOR_RGB2HSV);

        int rCnt, gCnt, pCnt;
        Mat redThresh0 = new Mat();
        Mat redThresh1 = new Mat();
        Mat greenThresh = null;
        //Red
        inRange(cardHSV, new Scalar(0, 15, 100), new Scalar(15, 255, 245), redThresh0); //red
        inRange(cardHSV, new Scalar(165, 50, 100), new Scalar(180, 255, 245), redThresh1); //pinkish
        bitwise_or(redThresh0,redThresh1,cardThreshold);
        rCnt = countNonZero(cardThreshold);
        //redThresh0 =cardThreshold.clone();

        //Green
        inRange(cardHSV, new Scalar(50, 50, 100), new Scalar(80, 255, 245), cardThreshold);
        //inRange(cardHSV, new Scalar(45, 40, 100), new Scalar(80, 255, 255), cardThreshold);
        gCnt = countNonZero(cardThreshold);
        //greenThresh = cardThreshold.clone();

        //Purple
        inRange(cardHSV, new Scalar(115, 50, 100), new Scalar(150, 255, 245), cardThreshold);
        //inRange(cardHSV, new Scalar(120, 40, 100), new Scalar(165, 255, 255), cardThreshold);
        pCnt = countNonZero(cardThreshold);
        //purpleThresh.clone();

        //cardThreshold = cardHSV; //TODO
        if (rCnt > gCnt && rCnt > pCnt) {
            color = colorEnum.RED;
            //cardThreshold = redThresh0;
        } else if (gCnt > rCnt && gCnt > pCnt) {
            color = colorEnum.GREEN;
            //cardThreshold = greenThresh;
        } else if (pCnt > rCnt && pCnt > gCnt) {
            color = colorEnum.PURPLE;
        }

        if (debug) {
            Log.d(TAG, "    Red cnt:  " + rCnt +
                    " Green cnt:  " + gCnt +
                    " Purple cnt:  " + pCnt);
        }

    }

    /* Card is valid if all properties of the card are detected */
    public boolean isValid() {
        return (color != colorEnum.INVALID && shade != shadeEnum.INVALID &&
                shape != shapeEnum.INVALID && number > 0);

    }

    public String decodeCard() {
        /*  Decode the Card attributes into a string
            example: "2,G,S"
         */
        char colorChar, shadeChar, shapeChar;

        switch (color) {
            case RED:
                colorChar = 'R';
                break;
            case GREEN:
                colorChar = 'G';
                break;
            case PURPLE:
                colorChar = 'P';
                break;
            default:
                colorChar = '?';
                break;
        }

        switch (shade) {
            case EMPTY:
                shadeChar = 'E';
                break;
            case LINES:
                shadeChar = 'L';
                break;
            case SOLID:
                shadeChar = 'S';
                break;
            default:
                shadeChar = '?';
                break;
        }

        switch (shape) {
            case DIAMOND:
                shapeChar = 'D';
                break;
            case OVAL:
                shapeChar = 'O';
                break;
            case SQUIGGLE:
                shapeChar = 'S';
                break;
            default:
                shapeChar = '?';
                break;
        }

        if (cardName != null) {
            return String.format("(%s)%d:%s:%s:%s", cardName, number, colorChar, shadeChar, shapeChar);
        } else {
            return String.format("%d:%s:%s:%s", number, colorChar, shadeChar, shapeChar);
        }
    }

    private void endodeCard(Classifier.Recognition result) {
        /*  Decode the Card attributes into a string
            example: "2,G,S"
         */
        String colorStr, shadeStr, shapeStr;
        String[] features = result.getTitle().split("_"); //example: 1_p_e_s

        if (features.length != 4) {
            //TODO: throw exception;
            return;
        }

        number = Integer.parseInt(features[0]);
        colorStr = features[1];
        shadeStr = features[2];
        shapeStr = features[3];


/*       shade = shadeEnum.SOLID;
        shade = shadeEnum.LINES;
        shade = shadeEnum.EMPTY;

        shape = shapeEnum.OVAL;
        shape = shapeEnum.SQUIGGLE;
        shape = shapeEnum.DIAMOND;*/

//FIXME: color done in detectColor
/*        switch (colorStr) {
            case "r":
                color =colorEnum.RED;
                break;
            case "g":
                color = colorEnum.GREEN;
                break;
            case "p":
                color = colorEnum.PURPLE;
                break;
            default:
                color = colorEnum.INVALID;
                break;
        }*/

        switch (shadeStr) {
            case "e":
                shade = shadeEnum.EMPTY;
                break;
            case "l":
                shade = shadeEnum.LINES;
            case "s":
                shade = shadeEnum.SOLID;
                break;
            default:
                shade = shadeEnum.INVALID;
                break;
        }

        switch (shapeStr) {
            case "d":
                shape = shapeEnum.DIAMOND;
                break;
            case "o":
                shape = shapeEnum.OVAL;
                break;
            case "s":
                shape = shapeEnum.SQUIGGLE;
                break;
            default:
                shape = shapeEnum.INVALID;
                break;
        }

    }



    @Override
    public String toString() {
        return decodeCard();
    }

    /* Color transform */
    private void colorTransform(Mat in, double tran[], Mat out) {

        List<Mat> channels = new ArrayList<Mat>();
        Core.split(in, channels);

        for (int k = 0; k < channels.size(); k++) {

            Mat channel = channels.get(k);

            Size channel_size = channel.size();  //this should have only one channel
            for (int x = 0; x < channel_size.width; x++) {
                for (int y = 0; y < channel_size.height; y++) {
                    try {
                        channel.put(x, y, channel.get(x, y)[0] * tran[k]);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            Core.merge(channels, out);
        }
    }

    private void adaptiveCanny(Mat in,Mat out) {
        double sigma = 0.33;
        Mat blur= new Mat();
        //Imgproc.medianBlur(in, blur, 15); //TODO: I'm not sure which blur is best
        Imgproc.GaussianBlur(in, blur, new Size(11,11),0);
        Scalar mean = Core.mean(blur);

        /*
        from: http://www.pyimagesearch.com/2015/04/06/zero-parameter-automatic-canny-edge-detection-with-python-and-opencv/
        # apply automatic Canny edge detection using the computed median
        lower = int(max(0, (1.0 - sigma) * v))
        upper = int(min(255, (1.0 + sigma) * v))
        */

        int lower = (int) Math.max(0, (1.0 - sigma) * mean.val[0]);
        int upper = (int) Math.min(255, (1.0 + sigma) * mean.val[0]);

        Imgproc.Canny(blur, out, lower, upper, 3, false);
    }


    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    /* Used for threading */
    @Override
    public void run() {
        //processCardWarp(warpBox, parrentImage);
        processCard();
    }


    enum colorEnum {
        RED, GREEN, PURPLE, INVALID
    }

    enum shapeEnum {
        OVAL, SQUIGGLE, DIAMOND, INVALID
    }

    enum shadeEnum {
        EMPTY, LINES, SOLID, INVALID
    }
}