package com.rtpike.setsolver;

import android.content.Context;
import android.graphics.Bitmap;
//import android.os.AsyncTask;
import android.util.Log;


import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import java.lang.Math;


/**
 * Created by rtpike on 1/21/2016.
 * Identity cards in the camera images
 * Does not decode individual card images
 */
public class processImage { //} extends AsyncTask<Card, Void, Integer> {

    private static final String TAG = "processImage";
    public boolean debug=false;
    /*
    Detects cards by finding contours
     */
    public List<Card[]> mySets = new ArrayList<Card[]>();
    private List<Card> cards = null; //new ArrayList<Card>();
    private int numCards = 0;
    //private boolean setFound = false;
    private Mat inputImage = null;
    private static Context context;

    processImage(Context c) {
        context = c;
    }


    public int numCards() {
        /* Returns number of cards detected */
        return this.numCards;
    }

    public synchronized Mat detectCards() {
        return detectCards(inputImage);
    }

    public synchronized Mat detectCards(Mat in) {

        Mat outMat = in.clone();  //copy of input so we can draw on it without messing up card detection
        Mat gray = new Mat();
        Mat blur = new Mat();  //debug
        Mat thresh = new Mat();
        Mat hierarchy = new Mat();
        this.cards = new ArrayList<>();
        mySets = new ArrayList<Card[]>();

        double peri, area;
        List<MatOfPoint> contours = new ArrayList<>();
        //Size ksize = new Size(1, 1);

        Imgproc.cvtColor(in, gray, Imgproc.COLOR_RGB2GRAY);

        //INFO: extra function left commented for debug and test now filters
        //Imgproc.GaussianBlur(gray, blur, ksize, 500);
        //Imgproc.Canny(gray, thresh, 1500, 3000, 5, false);
        adaptiveCanny(gray, thresh);
        ///Imgproc.threshold(gray, thresh, 155, 255, Imgproc.THRESH_BINARY);
        ///Imgproc.medianBlur(gray, blur, 11);
        //Imgproc.adaptiveThreshold(gray, thresh, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 11, 1);
        Imgproc.findContours(thresh.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        //FIXME Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        //in = thresh; //debug
        //Imgproc.drawContours(thresh, contours, 0, new Scalar(0, 100, 255),-1);

        int numCards = 0;
        int contours_size = contours.size();
        for (int i = 0; i < contours_size; i++) {
            //System.out.println(Imgproc.contourArea(contours.get(i)));

            MatOfPoint card = contours.get(i);
            MatOfPoint2f card_2f = new MatOfPoint2f(card.toArray());
            MatOfPoint2f approxCurve = new MatOfPoint2f();

            peri = Imgproc.arcLength(card_2f, true);
            Imgproc.approxPolyDP(card_2f, approxCurve, 0.02 * peri, true);

            area = Imgproc.contourArea(approxCurve);

            if (area > 4000) {

                Point[] curve_vertices = approxCurve.toArray();
                if (curve_vertices.length == 4) {


/*                    Point[] vertices;
                    if (debug) {
                        vertices = new Point[4];
                        rect.points(vertices);
                        for (int j = 0; j < 4; j++) {
                            Imgproc.line(in, vertices[j], vertices[(j + 1) % 4], new Scalar(100, 100, 255));
                        }
                        //Imgproc.rectangle(in, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(255, 0, 0));
                    }*/

                    //Mat ROI = in.submat(rectB.y, rectB.y + rectB.height, rectB.x, rectB.x + rectB.width);
                    //FIXME rotateSubImage(rect, in, ROI);

                    Mat ROI = new Mat();
                    warpSubImage(approxCurve, in, ROI);

                    Card cardObj = new Card(context, ROI);
                    //Card cardObj = new Card();
                    //Card cardObj = new Card(approxCurve, in);;
                    cardObj.cardName = String.format("%d", numCards); //used for debugging
                    cardObj.corners = curve_vertices.clone();  //corners of card
                    cards.add(cardObj);  // add to list

                    //cardObj.processCard();
                    //cardObj.processCardWarp(approxCurve, in);

                    cardObj.thread = new Thread(cardObj);
                    cardObj.thread.start();
                    numCards++;

                }
                //FIXME if (true) return cardObj.cardImg_markup; //debug
            }
        }


        this.numCards = numCards; //class var

        // highlight cards
        for (int i = 0; i < cards.size(); i++) {
            Card cardObj = cards.get(i);
            if (cardObj.thread != null ) {
                try {
                    cardObj.thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
            //FIXME while (cardObj.getStatus() != AsyncTask.Status.FINISHED){ } //wait until card is done
            Scalar color;
            if (cardObj.isValid()) {
                color = new Scalar(0, 0, 255); //blue
            } else {
                color = new Scalar(255, 0, 0); //Red
            }

            if (debug) {

                int p = 0;
                for (p = 0; p < cardObj.corners.length; p++) {
                    Imgproc.line(outMat, cardObj.corners[p], cardObj.corners[(p + 1) % cardObj.corners.length], color, 3);
                }
            }

            if (debug) {  //debug
                if (cardObj.corners[0].x < cardObj.corners[1].x) {
                    Imgproc.putText(outMat, cardObj.decodeCard(), cardObj.corners[0], Core.FONT_HERSHEY_PLAIN, 2, new Scalar(0, 240, 240));
                } else {
                    Imgproc.putText(outMat, cardObj.decodeCard(), cardObj.corners[1], Core.FONT_HERSHEY_PLAIN, 2, new Scalar(0, 240, 240));
                }
            }
        }
        // Search for a set and draw a green box around the cards
        Card setCards[] = new Card[3];



        if (findSet(mySets)) {
            for (int c = 0; c < mySets.size(); c++) { //Set (3 cards)
                 Scalar color = new Scalar(c*10, 255-c*45, c*45); //Green

                setCards = mySets.get(c); //TODO: got trough all lists
                Log.d(TAG, "Set:");

                for (int i = 0; i < setCards.length; i++) { //Card
                    Log.d(TAG, "    " + setCards[i].decodeCard());

                    for (int p = 0; p < setCards[i].corners.length; p++) { //Draw Boarder Lines
                        Imgproc.line(outMat, setCards[i].corners[p], setCards[i].corners[(p + 1) % 4], color, 7);
                    }
                }
            }
        }

        return outMat;
    }

    private void rotateSubImage(RotatedRect rect, Mat in, Mat cropped) {
        /* Rotate and crop the given image */

        // matrices we'll use
        Mat rotMat, out = new Mat();
        // get angle and size from the bounding box
        double angle = rect.angle;
        Size rect_size = rect.size;

        if (rect.angle < -45.) {
            angle += 90.0;
            Double tmp;
            tmp = rect_size.width;
            rect_size.width = rect_size.height;
            rect_size.height = tmp;
        }

        //Get rid of card boarders
        rect_size.width = rect_size.width * 0.9;
        rect_size.height = rect_size.height * 0.9;

        // get the rotation matrix
        rotMat = Imgproc.getRotationMatrix2D(rect.center, angle, 1.0);  // <<<<<<<<<<<<
        // perform the affine transformation
        Imgproc.warpAffine(in, out, rotMat, in.size(), Imgproc.INTER_CUBIC); //<<<<<<<<<<<<
        // crop the resulting image
        Imgproc.getRectSubPix(out, rect_size, rect.center, cropped);


    }

    /**
     * Return the distance between the two points
     * @param pts0
     * @param pts1
     * @return
     */
    private double pointNorm(Point pts0, Point pts1) {
        double x = pts0.x - pts1.x;
        double y = pts0.y - pts1.y;

        return Math.sqrt(Math.pow(x,2) + Math.pow(y,2));
    }


    private void warpSubImage(MatOfPoint2f warpBox, Mat in, Mat cropped) {
        /* Rotate and crop the given image */



        Point points[] = new Point[4];

        // 250x350

/*        points[0] =  new Point(0,0);
        points[1] =  new Point(350,0);
        points[2] =  new Point(350,350);
        points[3] =  new Point(0,350);
        MatOfPoint2f destWarp = new MatOfPoint2f(points);*/

        //rotate warpBox so longest side is horizontal
        Point[] curve_vertices = warpBox.toArray();
        //if (curve_vertices.length == 4) {  //TODO: check length
        double height_dist = pointNorm(curve_vertices[0], curve_vertices[1]);
        double width_dist = pointNorm(curve_vertices[1], curve_vertices[2]);
        if (height_dist < width_dist) {
            //rotate points
            points[3] =  new Point(0,0);
            points[0] =  new Point(350,0);
            points[1] =  new Point(350,350);
            points[2] =  new Point(0,350);
        } else {  //don't rotate
            points[0] =  new Point(0,0);
            points[1] =  new Point(350,0);
            points[2] =  new Point(350,350);
            points[3] =  new Point(0,350);
        }
        MatOfPoint2f destWarp = new MatOfPoint2f(points);

        Mat transform = Imgproc.getPerspectiveTransform(warpBox,destWarp);
        //poker card size 822x1122
        Imgproc.warpPerspective(in, cropped, transform, new Size(350, 350), Imgproc.INTER_NEAREST);
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

    public void loadTestImage(Context context, int resourceId) {
        try {
            //Mat tempMat = new Mat();
            inputImage = Utils.loadResource(context, resourceId, Imgcodecs.CV_LOAD_IMAGE_COLOR);
            Imgproc.cvtColor(inputImage, inputImage, Imgproc.COLOR_BGR2RGB);

        } catch (Exception except) {
            Log.e(TAG, except.getMessage(), except);
        }

    }

    public Bitmap getBitmap() {
        Bitmap img = Bitmap.createBitmap(inputImage.cols(), inputImage.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(inputImage, img);
        return img;
        //imgView.setImageBitmap(img);

    }

    public List<Card> getCards() { return cards;}

    /* brute force method to find a set
    */
    private boolean findSet(List<Card[]> setCards) {

        //setCards = new Card[3];
        //At least three cards are needed to find a set
        int card_size = cards.size();
        if (card_size < 3 ) { return false; }

        for (int i=0;i < card_size; i++ ){ //find all sets
            for (int j=i+1;j < card_size;j++){
                for (int k=j+1;k < card_size;k++){
                    if (isSet(cards.get(i),cards.get(j),cards.get(k))) {
                        setCards.add(new Card[] {cards.get(i),cards.get(j),cards.get(k)}); //set found

                    }
                }
            }
        }

        // no set found
        return setCards.size() > 0;
    }


    private boolean isSet(Card card0,Card card1,Card card2){
        /*
        private  int number=-1; //number of shapes
        private  colorEnum color=colorEnum.INVALID;  //red=0,green=1,purple=2
        private  shadeEnum shade=shadeEnum.INVALID;  //0=empty, 1=lines, 2=solid
        private  shapeEnum shape=shapeEnum.INVALID;  //"oval", "squiggle", "diamond"
        */

        boolean numberSet=false;
        boolean colorSet=false;
        boolean shadeSet=false;
        boolean shapeSet=false;

        //if any of the cards are not valid bale
        if (!(card0.isValid() && card1.isValid() && card2.isValid())) {
            return false;
        }

        numberSet = (card0.number == card1.number && card1.number == card2.number ) ||
                (card0.number != card1.number && card1.number != card2.number && card0.number != card2.number );

        colorSet = (card0.color == card1.color && card1.color == card2.color ) ||
                (card0.color != card1.color && card1.color != card2.color && card0.color != card2.color);

        shadeSet = (card0.shade == card1.shade && card1.shade == card2.shade ) ||
                (card0.shade != card1.shade && card1.shade != card2.shade && card0.shade != card2.shade);

        shapeSet = (card0.shape == card1.shape && card1.shape == card2.shape ) ||
                (card0.shape != card1.shape && card1.shape != card2.shape && card0.shape != card2.shape);

        return numberSet && colorSet && shadeSet && shapeSet;
    }


    private Mat testImage() {

        //Debug
        Mat image = new Mat(new Size(800,480),CvType.CV_8UC1);
        Mat rgbLoadedImage = new Mat();
        //File root = Environment.getExternalStorageDirectory();
        //context.getResources().getDrawable(R.drawable.set_test)''
        String imageUri = "drawable://" + R.drawable.set_test2;
        //File file = new File(getAssets().open("set_test.jpg");
        image = Imgcodecs.imread(imageUri);
        //image = Utils.loadResource(MainActivity, R.drawable.test, Imgcodecs.CV_LOAD_IMAGE_COLOR);
        //image = Imgcodecs.imread(file.getAbsolutePath(), Imgproc.COLOR_BGR2GRAY);

        if(image.width()>0)

        {

            rgbLoadedImage = new Mat(image.size(), image.type());
            Imgproc.cvtColor(image, rgbLoadedImage, Imgproc.COLOR_BGR2RGB);

            if (debug)
                Log.d(TAG, "loadedImage: " + "chans: " + image.channels()
                        + ", (" + image.width() + ", " + image.height() + ")");

            image.release();
            image = null;
        }

        return rgbLoadedImage;
    }

}
