package com.martin.opencv4android;

import android.app.FragmentManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private ImageView iv_show_img;
    private PolygonView polygonView;
    private String img_Decodable_Str;
    private Button scan;
    private FrameLayout sourceFrame;
    private Uri imageUri;
    private Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        iv_show_img = (ImageView) findViewById(R.id.iv_show_img);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new ScanButtonClickListener());
        polygonView = (PolygonView) findViewById(R.id.polygonView);
        Uri uri = getIntent().getParcelableExtra("imageUri");

        try {
            bitmap = Utils.getBitmap(MainActivity.this, uri);
            getContentResolver().delete(uri, null, null);
            sourceFrame = (FrameLayout) findViewById(R.id.sourceFrame);
            sourceFrame.post(new Runnable() {
                @Override
                public void run() {
                    Log.d("SOurcevrame", sourceFrame.getWidth() + " " + sourceFrame.getHeight());
                    Bitmap scaledBitmap = scaledBitmap(bitmap, sourceFrame.getWidth(), sourceFrame.getHeight());
                    iv_show_img.setImageBitmap(scaledBitmap);
                    Bitmap tempBitmap = ((BitmapDrawable) iv_show_img.getDrawable()).getBitmap();
                    Map<Integer, PointF> pointFs = getEdgePoints(tempBitmap);
                    polygonView.setPoints(pointFs);
                    polygonView.setVisibility(View.VISIBLE);
                    int padding = (int) getResources().getDimension(R.dimen.scanPadding);
                    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(tempBitmap.getWidth() + 2 * padding, tempBitmap.getHeight() + 2 * padding);
                    layoutParams.gravity = Gravity.CENTER;
                    polygonView.setLayoutParams(layoutParams);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        // scan.setOnClickListener(new ScanButtonClickListener());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    private Bitmap scaledBitmap(Bitmap bitmap, int width, int height) {
        Matrix m = new Matrix();
        m.setRectToRect(new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()), new RectF(0, 0, width, height), Matrix.ScaleToFit.CENTER);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
    }

    private Map<Integer, PointF> getEdgePoints(Bitmap tempBitmap) {
        List<PointF> pointFs = getContourEdgePoints(tempBitmap);
        Map<Integer, PointF> orderedPoints = orderedValidEdgePoints(tempBitmap, pointFs);
        return orderedPoints;
    }

    private List<PointF> getContourEdgePoints(Bitmap tempBitmap) {
        int w = tempBitmap.getWidth(), h = tempBitmap.getHeight();
        int[] pix = new int[w * h];
        tempBitmap.getPixels(pix, 0, w, 0, 0, w, h);
        int[] points = OpenCVHelper.getBoxPoints(pix, w, h);
        float x1 = points[0];
        float y1 = points[1];
        float x2 = points[2];
        float y2 = points[3];

        float x3 = points[4];
        float y3 = points[5];
        float x4 = points[6];
        float y4 = points[7];

        List<PointF> pointFs = new ArrayList<>();
        pointFs.add(new PointF(x1, y1));
        pointFs.add(new PointF(x2, y2));
        pointFs.add(new PointF(x3, y3));
        pointFs.add(new PointF(x4, y4));
        return pointFs;
    }

    private Map<Integer, PointF> orderedValidEdgePoints(Bitmap tempBitmap, List<PointF> pointFs) {
        Map<Integer, PointF> orderedPoints = polygonView.getOrderedPoints(pointFs);
        if (!polygonView.isValidShape(orderedPoints)) {
            orderedPoints = getOutlinePoints(tempBitmap);
        }
        return orderedPoints;
    }

    private Map<Integer, PointF> getOutlinePoints(Bitmap tempBitmap) {
        Map<Integer, PointF> outlinePoints = new HashMap<>();
        outlinePoints.put(0, new PointF(0, 0));
        outlinePoints.put(1, new PointF(tempBitmap.getWidth(), 0));
        outlinePoints.put(2, new PointF(0, tempBitmap.getHeight()));
        outlinePoints.put(3, new PointF(tempBitmap.getWidth(), tempBitmap.getHeight()));
        return outlinePoints;
    }

    private class ScanButtonClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Map<Integer, PointF> points = polygonView.getPoints();
            Log.d("OpenCV4Android", String.valueOf(points.size()));
            Bitmap op = getScannedBitmap(bitmap, points);
            iv_show_img.setImageBitmap(op);
            if (isScanPointsValid(points)) {
//                Bitmap op = getScannedBitmap(bitmap, points);
//                iv_show_img.setImageBitmap(op);
            } else {
                // showErrorDialog();
            }
        }
    }

    private Bitmap getScannedBitmap(Bitmap original, Map<Integer, PointF> points) {
        int width = original.getWidth();
        int height = original.getHeight();
        float xRatio = (float) original.getWidth() / iv_show_img.getWidth();
        float yRatio = (float) original.getHeight() / iv_show_img.getHeight();
//         float xRatio = 1;
//         float yRatio = 1;

        int[] newpoints = {0,0,0,0,0,0,0,0};
        newpoints[0] = (int)((points.get(0).x) * xRatio);
        newpoints[1] = (int)((points.get(0).y) * yRatio);
        newpoints[2] = (int)((points.get(1).x) * xRatio);
        newpoints[3] = (int)((points.get(1).y) * yRatio);
        newpoints[4] = (int)((points.get(2).x) * xRatio);
        newpoints[5] = (int)((points.get(2).y) * yRatio);
        newpoints[6] = (int)((points.get(3).x) * xRatio);
        newpoints[7] = (int)((points.get(3).y) * yRatio);
        Log.d("OpenCV", String.valueOf(newpoints[0]));
        Log.d("OpenCV", String.valueOf(newpoints[1]));
        Log.d("OpenCV", String.valueOf(points.get(1).x));
        Log.d("OpenCV", String.valueOf(points.get(1).y));
        Log.d("OpenCV", String.valueOf(points.get(2).x));
        Log.d("OpenCV", String.valueOf(points.get(2).y));
        Log.d("OpenCV", String.valueOf(points.get(3).x));
        Log.d("OpenCV", String.valueOf(points.get(3).y));
        // Log.d("", "POints(" + x1 + "," + y1 + ")(" + x2 + "," + y2 + ")(" + x3 + "," + y3 + ")(" + x4 + "," + y4 + ")");
        int[] pix = new int[width * height];
        original.getPixels(pix, 0, width, 0, 0, width, height);
        Log.d("OpenCV4Android", "Came here");
        int[] resultPixes = OpenCVHelper.perspective(pix, newpoints, width, height);
        Log.d("OpenCV4Android", "Checkpint");
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        result.setPixels(resultPixes, 0, width, 0, 0, width, height);
        polygonView.setVisibility(View.GONE);
        return result;
    }

    private boolean isScanPointsValid(Map<Integer, PointF> points) {
        return points.size() == 4;
    }

}
