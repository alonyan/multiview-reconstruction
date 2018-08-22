package net.preibisch.mvrecon.headless.quality;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.headless.boundingbox.TestBoundingBox;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxMaximal;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.transformed.FusedRandomAccessibleInterval;
import net.preibisch.mvrecon.process.fusion.transformed.TransformView;
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.fusion.transformed.TransformWeight;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.quality.FRCRealRandomAccessible;
import net.preibisch.simulation.imgloader.SimulatedBeadsImgLoader;

public class TestQuality
{
	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		SpimData2 spimData;
		
		// generate 4 views with 1000 corresponding beads, single timepoint
		// spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		// load drosophila
		//spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml" );
		//spimData = new XmlIoSpimData2( "" ).load( "/Volumes/home/Data/brain/HHHEGFP_het.xml" );
		//spimData = new XmlIoSpimData2( "" ).load( "/Volumes/Samsung_T5/Fabio Testdata/half_new2/dataset_initial.xml");
		spimData = new XmlIoSpimData2( "" ).load( "/Volumes/Samsung_T5/CLARITY/dataset_fullbrainsection.xml");

		System.out.println( "Views present:" );

		for ( final ViewId viewId : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( Group.pvid( viewId ) );

			// select views to process
			final List< ViewId > viewIds = new ArrayList< ViewId >();

			//	for ( int i = 0; i <= 55; ++i  )
			//		viewIds.add( new ViewId( 0, i ) );
			//	for ( int i = 119; i <=174; ++i  )
			//		viewIds.add( new ViewId( 0, i ) );

			//for ( int i = 0; i <= 5; ++i  )
			//	viewIds.add( new ViewId( 0, i ) );

			viewIds.add( new ViewId( 0, 10 ) );

			// filter not present ViewIds
			final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );
	
			// re-populate not present ViewIds
			//updateMissingViews( spimData, viewIds );
			//BoundingBoxMaximal.ignoreMissingViews = true;
	
			testQuality( spimData, viewIds );
	}

	public static void updateMissingViews( final SpimData2 spimData, final List< ViewId > viewIds )
	{
		for ( final ViewId viewId : viewIds )
		{
			final ViewDescription vd  = spimData.getSequenceDescription().getViewDescription( viewId );

			if ( !vd.isPresent() )
			{
				for ( final ViewDescription vdc : spimData.getSequenceDescription().getViewDescriptions().values() )
				{
					if ( vd.getViewSetup().getAngle() == vdc.getViewSetup().getAngle() &&
							vd.getViewSetup().getChannel() == vdc.getViewSetup().getChannel() &&
							vd.getViewSetup().getTile() == vdc.getViewSetup().getTile() &&
							vdc.getViewSetupId() != vd.getViewSetupId() )
					{
						System.out.println( "Missing view " + Group.pvid( vd ) + " compensated from " + Group.pvid( vdc ) );

						final ViewRegistration vrc = spimData.getViewRegistrations().getViewRegistration( vdc );
						vrc.updateModel();

						final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( vdc );
						vr.getTransformList().clear();
						vr.getTransformList().addAll( vrc.getTransformList() );
						vr.updateModel();
					}
				}
			}
		}
	}

	public static Interval trimInterval( final Interval interval )
	{
		final long[] min = new long[ interval.numDimensions() ];
		final long[] max = new long[ interval.numDimensions() ];

		for ( int d = 0; d < interval.numDimensions(); ++d )
		{
			min[ d ] = interval.min( d ) + 10;
			max[ d ] = interval.max( d ) - 10;
		}

		return new FinalInterval( min, max );
	}

	public static void testQuality( final SpimData2 spimData, final List< ViewId > viewIds )
	{
		Interval bb = new BoundingBoxMaximal( viewIds, spimData ).estimate( "Full Bounding Box" );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": bounding box = " + bb );

		bb = trimInterval( bb );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": cropped bounding box = " + Util.printInterval( bb ) );

		// img loading and registrations
		final ImgLoader imgLoader = spimData.getSequenceDescription().getImgLoader();
		final ViewRegistrations registrations = spimData.getViewRegistrations();

		final ArrayList< Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > > data = new ArrayList<>();

		for ( final ViewId viewId : viewIds )
		{
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Loading view " +  Group.pvid( viewId ) + " ..." );

			final RandomAccessibleInterval input = imgLoader.getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId() );
			//DisplayImage.getImagePlusInstance( input, true, "Fused, Virtual", 0, 255 ).show();

			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Computing FRC for " +  Group.pvid( viewId ) + " ..." );

			final FRCRealRandomAccessible< FloatType > frc = FRCRealRandomAccessible.distributeGridFRC( input, 0.1, 20, 256, true, null );
			//DisplayImage.getImagePlusInstance( frc.getRandomAccessibleInterval(), true, "Fused, Virtual", Double.NaN, Double.NaN ).show();

			final ViewRegistration vr = registrations.getViewRegistration( viewId );
			vr.updateModel();

			data.add( new ValuePair<>( frc.getRandomAccessibleInterval(), vr.getModel() ) );
		}

		// downsampling
		double downsampling = 4; //Double.NaN;

		//
		// display virtually fused
		//

		final RandomAccessibleInterval< FloatType > virtual = fuseRAIs( data, downsampling, bb, 1 );
		DisplayImage.getImagePlusInstance( virtual, false, "Fused, Virtual" + viewIds.get( 0 ).getViewSetupId(), Double.NaN, Double.NaN ).show();

	}

	public static RandomAccessibleInterval< FloatType > fuseRAIs(
			final Collection< Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > > data,
			final double downsampling,
			final Interval boundingBox,
			final int interpolation )
	{
		final Interval bb;

		if ( !Double.isNaN( downsampling ) )
			bb = TransformVirtual.scaleBoundingBox( boundingBox, 1.0 / downsampling );
		else
			bb = boundingBox;

		final long[] dim = new long[ bb.numDimensions() ];
		bb.dimensions( dim );

		final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();
		final ArrayList< RandomAccessibleInterval< FloatType > > weights = new ArrayList<>();

		for ( final Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > d : data )
		{
			AffineTransform3D model = d.getB();

			if ( !Double.isNaN( downsampling ) )
			{
				model = model.copy();
				TransformVirtual.scaleTransform( model, 1.0 / downsampling );
			}

			images.add( TransformView.transformView( d.getA(), model, bb, 0, interpolation ) );

			final float[] blending = Util.getArrayFromValue( FusionTools.defaultBlendingRange, 3 );
			final float[] border = Util.getArrayFromValue( FusionTools.defaultBlendingBorder, 3 );

			// adjust both for z-scaling (anisotropy), downsampling, and registrations itself
			
			FusionTools.adjustBlending( getDimensions( d.getA() ), "", blending, border, model );

			weights.add( TransformWeight.transformBlending( d.getA(), border, blending, model, bb ) );
		}

		return new FusedRandomAccessibleInterval( new FinalInterval( dim ), images, weights );
	}

	public static FinalDimensions getDimensions( final Interval interval )
	{
		final long[] dim = new long[ interval.numDimensions() ];

		for ( int d = 0; d < dim.length; ++d )
			dim[ d ] = interval.dimension( d );

		return new FinalDimensions( dim );
	}
}