package qupath.lib.ij_opencv;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/*
 * This code was copied (and very slightly changed) from the IJ-OpenCV project. 
 * The Actual library could not be used because its version of OpenCV conflicts 
 * with the one used in QuPath. 
 *
 * The full library can be found at https://github.com/joheras/IJ-OpenCV
 */

public class MatToImagePlusConverter {

    public static ImageProcessor toImageProcessor(Mat mat) {

        final int type = mat.type();

        ImageProcessor result = null;



        if (type == CvType.CV_8UC1) { // type = BufferedImage.TYPE_BYTE_GRAY;

            result = makeByteProcessor(mat);

        } else if (type == CvType.CV_8UC3) {	// type = BufferedImage.TYPE_3BYTE_BGR;

            result = makeColorProcessor(mat); // faulty 

        } else if (type == CvType.CV_16UC1) {	// signed short image

            result = makeShortProcessor(mat);

        } else if (type == CvType.CV_32FC1) {	// float image

            result = makeFloatProcessor(mat);

        } else {

            throw new IllegalArgumentException("cannot convert Mat of type " + type);

        }

        return result;

    }



    // private methods ----------------------------------------------

    private static ByteProcessor makeByteProcessor(Mat mat) {

        if (mat.type() != CvType.CV_8UC1) {

            throw new IllegalArgumentException("wrong Mat type: " + mat.type());

        }

        final int w = mat.cols();

        final int h = mat.rows();

        ByteProcessor bp = new ByteProcessor(w, h);

        mat.get(0, 0, (byte[]) bp.getPixels());
        
        return bp;

    }



    private static ShortProcessor makeShortProcessor(Mat mat) {

        if (mat.type() != CvType.CV_16UC1) {

            throw new IllegalArgumentException("wrong Mat type: " + mat.type());

        }

        final int w = mat.cols();

        final int h = mat.rows();

        ShortProcessor sp = new ShortProcessor(w, h);

        //ShortPointer sptr = new ShortPointer(mat.data());

        //sptr.get((short[]) sp.getPixels());

        //sptr.close();
        mat.get(0, 0, (short[]) sp.getPixels());

        return sp;

    }



    private static FloatProcessor makeFloatProcessor(Mat mat) {

        if (mat.type() != CvType.CV_32FC1) {

            throw new IllegalArgumentException("wrong Mat type: " + mat.type());

        }

        final int w = mat.cols();

        final int h = mat.rows();

        FloatProcessor fp = new FloatProcessor(w, h);

        mat.get(0, 0, (float[]) fp.getPixels());

        return fp;

    }



    private static ColorProcessor makeColorProcessor(Mat mat) {

        if (mat.type() != CvType.CV_8UC3) {

            throw new IllegalArgumentException("wrong Mat type: " + mat.type());

        }

        final int w = mat.cols();

        final int h = mat.rows();

        byte[] pixels = new byte[w * h * (int) mat.elemSize()];

        mat.get(0,0,pixels);

        // convert byte array to int-encoded RGB values

        ColorProcessor cp = new ColorProcessor(w, h);

        int[] iData = (int[]) cp.getPixels();

        for (int i = 0; i < w * h; i++) {

            int red = pixels[i * 3 + 0] & 0xff;

            int grn = pixels[i * 3 + 1] & 0xff;

            int blu = pixels[i * 3 + 2] & 0xff;

            iData[i] = (red << 16) | (grn << 8) | blu;

        }

        return cp;

    }



}