package spim.process.deconvolution2.iteration;

import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;

public abstract class ComputeBlockThreadAbstract implements ComputeBlockThread
{
	final float minValue;
	final int id;
	final int[] blockSize;
	final public Img< FloatType > psiBlockTmp;

	/**
	 * Instantiate a block thread
	 * 
	 * @param minValue - the minimum value inside the deconvolved image
	 * @param blockSize - the block size in which we process
	 * @param id - the unique id of this thread, >= 0, starting at 0 and increasing by 1 each thread
	 */
	public ComputeBlockThreadAbstract(
			final ImgFactory< FloatType > blockFactory,
			final float minValue,
			final int[] blockSize,
			final int id )
	{
		this.psiBlockTmp = blockFactory.create( Util.int2long( blockSize ), new FloatType() );
		this.minValue = minValue;
		this.blockSize = blockSize;
		this.id = id;
	}

	/**
	 * Instantiate a block thread
	 * 
	 * @param minValue - the minimum value inside the deconvolved image
	 * @param blockSize - the block size in which we process
	 * @param id - the unique id of this thread, >= 0, starting at 0 and increasing by 1 each thread
	 */
	public ComputeBlockThreadAbstract(
			final float minValue,
			final int[] blockSize,
			final int id )
	{
		this( new ArrayImgFactory<>(), minValue, blockSize, id );
	}

	/**
	 * @return the block size in which we process
	 */
	@Override
	public int[] getBlockSize() { return blockSize; }

	/**
	 * @return the minimum value inside the deconvolved image
	 */
	@Override
	public float getMinValue() { return minValue; }

	/**
	 * @return the unique id of this thread, >= 0
	 */
	@Override
	public int getId() { return id; }

	/**
	 * contains the deconvolved image at the current iteration, copied into a block from outside (outofbounds is mirror)
	 *
	 * @return the Img to use in order to provide the copied psiBlock
	 */
	public Img< FloatType > getPsiBlockTmp() { return psiBlockTmp; }
}
