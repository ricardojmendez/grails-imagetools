package org.grails.plugins.imagetools

import java.awt.RenderingHints
import java.io.*;
import java.awt.image.renderable.*;
import javax.media.jai.*;
import com.sun.media.jai.codec.*;

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

	/* Removes the accelaration lib exception */
	static { System.setProperty("com.sun.media.jai.disableMediaLib", "true"); }

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
		FileOutputStream os = new FileOutputStream(file);
		JAI.create("encode", result, os, type, null);
		os.close()
	}
	
	/**
     * Returns the resulting image as a byte array.
     *
     * @param type file type for the image
     * @see <a href="http://java.sun.com/products/java-media/jai/iio.html">Possible JAI encodings</a>
     */
    public byte[] getBytes(String type) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JAI.create("encode", result, bos, type, null);
        return bos.toByteArray()
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
	 * This method creates a thumbnail of the maxWidth and maxHeight it takes as a parameter
	 *
	 * Example : Calling the method thumnailSpecial(640, 480, 1, 1)
	 * will never produce images larger than 640 on the width, and never larger than 480 on the height and use
	 * InterpolationBilinear(8) and scale
	 *
	 * @param maxWidth
	 * The maximum width the thumbnail is allowed to have
	 *
	 * @param maxHeigth
	 * The maximum height the thumbnail is allowed to have
	 *
	 * @param interPolationType
	 * Is for you to choose what interpolation you wish to use
	 * 1 : InterpolationBilinear(8) // Produces good image quality with smaller image size(byte) then the other two
	 * 2 : InterpolationBicubic(8)  // Supposed to produce better than above, but also larger size(byte)
	 * 3 : InterpolationBicubic2(8) // Supposed to produce the best of the three, but also largest size(byte)
	 *
	 * @param renderingType
	 * Too choose the rendering type
	 * 1: Uses scale // Better on larger thumbnails
	 * 2: Uses SubsampleAverage  // Produces clearer images when it comes to really small thumbnail e.g 80x60
	 */
	public void thumbnailSpecial(float maxWidth, float maxHeight, int interPolationType, int renderingType) {
		if (height <= maxHeight && width <= maxWidth) {
			/* Don't change, keep it as it is, even though one might loose out on the compression included below (not sure)*/
			result = image
		}
		else {
			boolean tall = (height * (maxWidth / maxHeight) > width);
			double modifier = maxWidth / (float) (tall ? (height * (maxWidth / maxHeight)) : width);
			ParameterBlock params = new ParameterBlock();
			params.addSource(image);

			// We had to do this because of that the different rendering 
			// options require either float or double.  This ended up having
			// a side effect of Java apparently not correctly converting from
			// floats to double, so we have to keep the value as a double
			// and then convert to float when necessary
			switch (renderingType) {
				case 1: 
					params.add((float)modifier);//x scale factor
					params.add((float)modifier);//y scale factor
					break;
				case 2: 
					params.add(modifier);//x scale factor
					params.add(modifier);//y scale factor
					break;
				default:
					params.add((float)modifier);//x scale factor
					params.add((float)modifier);//y scale factor
					break;
			}

			params.add(0.0F);//x translate
			params.add(0.0F);//y translate
			switch (interPolationType) {
				case 1: params.add(new InterpolationBilinear(8)); break; // Produces good image quality with smaller image size(byte) then the other two
				case 2: params.add(new InterpolationBicubic(8)); break;  // Supposed to produce better than above, but also larger size(byte)
				case 3: params.add(new InterpolationBicubic2(8)); break; // Supposed to produce the best of the two, but also largest size(byte)
				default: params.add(new InterpolationBilinear(8)); break;
			}

			switch (renderingType) {
				case 1: result = JAI.create("scale", params); break;
				case 2:
					RenderingHints qualityHints = new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
					result = JAI.create("SubsampleAverage", params, qualityHints); break;
				default: result = JAI.create("scale", params); break;
			}
		}
	}

	public void setImageToNull() {
		image = null;
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
}