package com.t_oster.visicam;

import com.googlecode.javacv.FrameGrabber.Exception;
import com.googlecode.javacv.OpenCVFrameGrabber;
import static com.googlecode.javacv.cpp.opencv_calib3d.*;
import static com.googlecode.javacv.cpp.opencv_core.*;
import com.googlecode.javacv.cpp.opencv_core.CvMat;
import com.googlecode.javacv.cpp.opencv_core.CvMemStorage;
import com.googlecode.javacv.cpp.opencv_core.CvPoint;
import com.googlecode.javacv.cpp.opencv_core.CvPoint2D32f;
import com.googlecode.javacv.cpp.opencv_core.CvPoint3D32f;
import com.googlecode.javacv.cpp.opencv_core.CvSeq;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class CameraController 
{
  
  public InputStream toJpegStream(final BufferedImage img) throws IOException
  {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ImageIO.write(img, "jpg", bos);
    return new ByteArrayInputStream(bos.toByteArray());
  }
  
  public BufferedImage takeSnapshot(int cameraIndex, int width, int height) throws Exception
  {
    OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(cameraIndex);
    grabber.setImageHeight(height);
    grabber.setImageWidth(width);
    grabber.start();
    IplImage img = grabber.grab();
    BufferedImage result = img.getBufferedImage();
    grabber.stop();
    return result;
  }
  
  public RelativePoint findMarker(BufferedImage input, RelativeRectangle roi)
  {
    Rectangle abs = roi.toAbsoluteRectangle(input.getWidth(), input.getHeight());
    IplImage in = IplImage.createFrom(input.getSubimage(abs.x, abs.y, abs.width, abs.height));
    IplImage gray = IplImage.create(in.width(), in.height(), in.depth(), 1);
    cvCvtColor(in, gray, CV_BGR2GRAY);
    cvErode(gray, gray, null, 1);
    cvErode(gray, gray, null, 1);
    cvDilate(gray, gray, null, 1);
    cvDilate(gray, gray, null, 1);
    cvErode(gray, gray, null, 1);
    cvDilate(gray, gray, null, 1);
    CvMemStorage storage = cvCreateMemStorage(0);
    double minDist = 10;
    double cannyThreshold = 50;
    double minVotes = 15;
    int minRad = 5;
    int maxRad = 25;
    CvSeq circles = cvHoughCircles(gray, storage, CV_HOUGH_GRADIENT, 1, minDist, cannyThreshold, minVotes, minRad, maxRad);
    if (circles.total() >= 1)
    {
      CvPoint3D32f point = new CvPoint3D32f(cvGetSeqElem(circles, 0));
      CvPoint center = cvPointFrom32f(new CvPoint2D32f(point.x(), point.y()));
      RelativePoint result = new RelativePoint((center.x()+abs.x)/(double)input.getWidth(), (center.y()+abs.y)/(double)input.getHeight());
      return result;
    }
    return null;
  }
  
  public BufferedImage applyHomography(BufferedImage img, RelativePoint[] markerPositions, double ouputWidth, double outputHeight)
  {
    CvMat src = CvMat.create(markerPositions.length, 1, CV_32FC(2));
    for (int i = 0; i < markerPositions.length; i++)
    {
      Point p = markerPositions[i].toAbsolutePoint(img.getWidth(), img.getHeight());
      src.put(i, 0, 0, p.x);
      src.put(i, 0, 1, p.y);
    }
    CvMat dst = CvMat.create(4, 1, CV_32FC(2));
    dst.put(0, 0, 0, 0);
    dst.put(0, 0, 1, 0);
    
    dst.put(1, 0, 0, img.getWidth());
    dst.put(1, 0, 1, 0);
    
    dst.put(2, 0, 0, 0);
    dst.put(2, 0, 1, img.getHeight());
    
    dst.put(3, 0, 0, img.getWidth());
    dst.put(3, 0, 1, img.getHeight());
    
    CvMat h = CvMat.create(3, 3);
    cvFindHomography(src, dst, h, CV_RANSAC, 1, null);
    IplImage in = IplImage.createFrom(img);
    cvWarpPerspective(in, in, h);
    return in.getBufferedImage();
  }
  
}
