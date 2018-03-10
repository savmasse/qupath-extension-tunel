package qupath.lib.ij_opencv;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

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

public class ImagePlusToMatConverter {
	
public static Mat toMat(ImageProcessor ip) {

        Mat mat = null;

        if (ip instanceof ByteProcessor) {

            mat = toMat((ByteProcessor) ip);

        } else if (ip instanceof ColorProcessor) {

            mat = toMat((ColorProcessor) ip);

        } else if (ip instanceof ShortProcessor) {

            mat = toMat((ShortProcessor) ip);

        } else if (ip instanceof FloatProcessor) {

            mat = toMat((FloatProcessor) ip);

        } else {

            throw new IllegalArgumentException("cannot convert to Mat: " + ip);

        }

        return mat;

    }



    /**

     * Duplicates {@link ByteProcessor} to the corresponding OpenCV image of

     * type {@link Mat}.

     *

     * @param bp The {@link ByteProcessor} to be converted

     * @return The OpenCV image (of type {@link Mat})

     */

    public static Mat toMat(ByteProcessor bp) {

        final int w = bp.getWidth();

        final int h = bp.getHeight();

        final byte[] pixels = (byte[]) bp.getPixels();



        // version A - copies the pixel data to a new array

//		Size size = new Size(w, h);

//		Mat mat = new Mat(size, opencv_core.CV_8UC1);

//		mat.data().put(bData);

        // version 2 - reuses the existing pixel array

        Mat res = new Mat(h, w, CvType.CV_8UC1);
        res.put(0, 0, pixels);
        
        return res;
    }



    /**

     * Duplicates {@link ShortProcessor} to the corresponding OpenCV image of

     * type {@link Mat}.

     *

     * @param bp The {@link ShortProcessor} to be converted

     * @return The OpenCV image (of type {@link Mat})

     */

    public static Mat toMat(ShortProcessor sp) {

        final int w = sp.getWidth();

        final int h = sp.getHeight();

        final short[] pixels = (short[]) sp.getPixels();

        Mat res = new Mat(h, w, CvType.CV_16UC1);
        res.put(0, 0, pixels);
        
        return res;
    }



    /**

     * Duplicates {@link FloatProcessor} to the corresponding OpenCV image of

     * type {@link Mat}.

     *

     * @param bp The {@link FloatProcessor} to be converted

     * @return The OpenCV image (of type {@link Mat})

     */

    public static Mat toMat(FloatProcessor cp) {

        final int w = cp.getWidth();

        final int h = cp.getHeight();

        final float[] pixels = (float[]) cp.getPixels();

        Mat res = new Mat(h, w, CvType.CV_32FC1);
        res.put(0, 0, pixels);
        
        return res;
    }



    /**

     * Duplicates {@link ColorProcessor} to the corresponding OpenCV image of

     * type {@link Mat}.

     *

     * @param bp The {@link ColorProcessor} to be converted

     * @return The OpenCV image (of type {@link Mat})

     */

    public static Mat toMat(ColorProcessor cp) {

        final int w = cp.getWidth();

        final int h = cp.getHeight();

        final int[] pixels = (int[]) cp.getPixels();

        byte[] bData = new byte[w * h * 3];



        // convert int-encoded RGB values to byte array

        for (int i = 0; i < pixels.length; i++) {

            bData[i * 3 + 0] = (byte) ((pixels[i] >> 16) & 0xFF);	// red

            bData[i * 3 + 1] = (byte) ((pixels[i] >> 8) & 0xFF);	// grn

            bData[i * 3 + 2] = (byte) ((pixels[i]) & 0xFF);	// blu

        }

        Mat res = new Mat(h, w, CvType.CV_8UC3);
        res.put(0, 0, bData);
        
        return res;
    }
}