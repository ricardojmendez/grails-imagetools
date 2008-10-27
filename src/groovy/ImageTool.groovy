import java.io.*;
import java.util.*;
import java.awt.image.renderable.*;
import javax.media.jai.*;
import com.sun.media.jai.codec.*
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/**
 * Helper class for handling images. Keeps a currently loaded image, as well 
 * as the result of applying operations to that image.  Operations will not 
 * affect the original image but will store the resulting image on the "result"
 * field.
 *
 * Based on the article http://www.evolt.org/article/Image_Manipulation_with_CFMX_and_JAI/18/33907/index.html
 * @see <a href="http://ricardo.strangevistas.net/jai-and-masking-operations.html">Masking operation gotchas</a>
 */
class ImageTool {
	static masks = [:];
	static alphas = [:];

	private RenderedOp original = null;
	private RenderedOp image = null;
	private RenderedOp result = null;
	private RenderedOp alpha = null;
	private RenderedOp mask = null;

	private boolean cropped = false;

	/**
	 * Should a thumbnail be created only if it will be smaller in size than
	 * the current image?
	 */
	boolean decreaseOnly = true;

	/**
	 * Returns the height for the currently loaded image
	 *
	 * @return height of the currently loaded image
	 */
	public getHeight() {
		return image.getHeight()
	}


	/**
	 * Returns the width for the currently loaded image
	 *
	 * @return width of the currently loaded image
	 */
	public getWidth() {
		return image.getWidth()
	}

	/**
	 * Saves a snapshot of the currently loaded image
	 *
	 */
	public void saveOriginal() {
		original = image.createSnapshot()
	}

	/**
	 * Restores a snapshot onto the original image.
	 *
	 */
	public void restoreOriginal() {
		image = original.createSnapshot()
	}

	/**
	 * Loads an image from a file.
	 *
	 * @param file path to the file from which the image should be loaded
	 */
	public void load(String file) {
		FileSeekableStream fss = new FileSeekableStream(file);
		image = JAI.create("stream", fss);
	}

	/**
	 * Loads a mask from a file and saves it on the cache, indexed by the file name
	 */
	public void loadMask(String file) {
		mask = ImageTool.masks[file]
		if (!mask) {
			FileSeekableStream fss = new FileSeekableStream(file);
			mask = JAI.create("stream", fss);
			masks[file] = mask
		}
	}

	/**
	 * Loads an alpha mask from a file and saves it on the cache
	 */
	public void loadAlpha(String file) {
		alpha = ImageTool.alphas[file]
		if (!alpha) {
			FileSeekableStream fss = new FileSeekableStream(file);
			alpha = JAI.create("stream", fss);
			alphas[file] = alpha;
		}
	}

	/**
	 * Overwrites the current image with the latest result image obtained.
	 */
	public void swapSource() {
		image = result;
		result = null;
	}

	/**
	 * Loads an image from a byte array.
	 *
	 * @param bytes array to be used for image initialization
	 */
	public void load(byte[] bytes) {
		ByteArraySeekableStream byteStream = new ByteArraySeekableStream(bytes);
		image = JAI.create("stream", byteStream);
	}

	/**
	 * Writes the resulting image to a file.
	 *
	 * @param file full path where the image should be saved
	 * @param type file type for the image
	 * @see <a href="http://java.sun.com/products/java-media/jai/iio.html">Possible JAI encodings</a>
	 */
	public void writeResult(String file, String type) throws IOException {
		if (type == "JPEG" && cropped) {
			// Workaround for JAI JPEG saving bug of no tile support (crop problems)
			BufferedImage bufferedImage = result.getRendering().getAsBufferedImage();

			try {
				FileOutputStream fos = new FileOutputStream(file);
				BufferedOutputStream bos = new BufferedOutputStream(fos);
				ImageIO.write(bufferedImage, type, bos);
				cropped = false;
			}
			catch (IOException e) {
				// handle errors
			}
		}
		else {
			FileOutputStream os = new FileOutputStream(file);
			JAI.create("encode", result, os, type, null);
			os.close()
		}

	}

	/**
	 * Creates a thumbnail of a maximum length and stores it in the result image
	 *
	 * @param edgeLength Maximum length
	 */
	public void thumbnail(float edgeLength) {
		if (height < edgeLength && width < edgeLength && decreaseOnly) {
			result = image
		}
		else {
			boolean tall = (height > width);
			float modifier = edgeLength / (float) (tall ? height : width);
			ParameterBlock params = new ParameterBlock();
			params.addSource(image);
			params.add(modifier);//x scale factor
			params.add(modifier);//y scale factor
			params.add(0.0F);//x translate
			params.add(0.0F);//y translate
			params.add(new InterpolationNearest());//interpolation method
			result = JAI.create("scale", params);
		}
	}

	/**
	 * Crops the image and stores the result
	 *
	 * @param edgeX Horizontal crop. The image will be cropped edgeX/2 on both sides.
	 * @param edgeY Vertical crop. The image will be cropped edgeY/2 on top and bottom.
	 */
	public void crop(float edgeX, edgeY) {
		ParameterBlock params = new ParameterBlock();
		params.addSource(image);
		params.add((float) (edgeX / 2));//x origin
		params.add((float) (edgeY / 2));//y origin
		params.add((float) (width - edgeX));//width
		params.add((float) (height - edgeY));//height
		result = JAI.create("crop", params);

		cropped = true
	}

	/**
	 * Crops the image to a square, centered, and stores it in the result image
	 *
	 */
	public void square() {
		float border = width - height
		float cropX, cropY
		if (border > 0) {
			cropX = border
			cropY = 0
		}
		else {
			cropX = 0
			cropY = -border
		}
		println "squaring: " + cropX + ", " + cropY
		crop(cropX, cropY)
	}

	/**
	 * Applies the currently loaded mask and alpha to the image
	 */
	public void applyMask() {
		ParameterBlock params = new ParameterBlock();
		params.addSource(mask);
		params.addSource(image);
		params.add(alpha);
		params.add(null);
		params.add(new Boolean(false));
		result = JAI.create("composite", params, null);
	}


	/**
	 * Creates a thumbnail of a maximum length and stores it in the result image
	 *
	 * @param edgeLength Maximum length
	 */
	public void thumbnailBiC(float edgeLength) {
		if (height < edgeLength && width < edgeLength && decreaseOnly) {
			result = image
		}
		else {
			boolean tall = (height * (400 / 300) > width);
			float modifier = edgeLength / (float) (tall ? height * (400 / 300) : width);
			ParameterBlock params = new ParameterBlock();
			params.addSource(image);
			params.add(modifier);//x scale factor
			params.add(modifier);//y scale factor
			params.add(0.0F);//x translate
			params.add(0.0F);//y translate
			params.add(new InterpolationBicubic(8));//interpolation method
			result = JAI.create("scale", params);
		}
	}

	/**
	 * Creates a thumbnail of a maximum length and stores it in the result image
	 *
	 * @param edgeLength Maximum length
	 */
	public void thumbnailBiC2(float edgeLength) {
		if (height < edgeLength && width < edgeLength && decreaseOnly) {
			result = image
		}
		else {
			boolean tall = (height * (400 / 300) > width);
			float modifier = edgeLength / (float) (tall ? height * (400 / 300) : width);
			ParameterBlock params = new ParameterBlock();
			params.addSource(image);
			params.add(modifier);//x scale factor
			params.add(modifier);//y scale factor
			params.add(0.0F);//x translate
			params.add(0.0F);//y translate
			params.add(new InterpolationBicubic2(8));//interpolation method
			result = JAI.create("scale", params);
		}
	}


	public void thumbnailBiL(float edgeLength) {
		if (height < edgeLength && width < edgeLength && decreaseOnly) {
			result = image
		}
		else {
			boolean tall = (height * (400 / 300) > width);
			float modifier = edgeLength / (float) (tall ? height * (400 / 300) : width);
			ParameterBlock params = new ParameterBlock();
			params.addSource(image);
			params.add(modifier);//x scale factor
			params.add(modifier);//y scale factor
			params.add(0.0F);//x translate
			params.add(0.0F);//y translate
			params.add(new InterpolationBilinear(8));//interpolation method
			result = JAI.create("scale", params);
		}
	}

}