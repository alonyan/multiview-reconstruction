package spim.fiji.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.pointspreadfunctions.PointSpreadFunction;
import spim.process.interestpointdetection.InterestPointTools;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;
import spim.process.psf.PSFExtraction;

public class PSF_Extract implements PlugIn
{
	public static String[] displayPSFChoice = new String[]{ "Do not show PSFs", "Show MIP of combined PSF's", "Show combined PSF's", "Show individual PSF's", "Show combined PSF's (original scale)", "Show individual PSF's (original scale)" };

	public static int defaultLabel = -1;
	public static boolean defaultCorresponding = true;
	public static int defaultPSFSizeX = 19;
	public static int defaultPSFSizeY = 19;
	public static int defaultPSFSizeZ = 25;

	@Override
	public void run( String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Dataset Fusion", true, true, true, true, true ) )
			return;

		extract( result.getData(), SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ) );
	}

	public static boolean extract(
			final SpimData2 spimData,
			final Collection< ? extends ViewId > viewCollection )
	{
		final ArrayList< ViewId > viewIds = new ArrayList<>();
		viewIds.addAll( viewCollection );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		if ( removed.size() > 0 ) IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// check which channels and labels are available and build the choices
		final String[] labels = InterestPointTools.getAllInterestPointLabels( spimData, viewIds );

		if ( labels.length == 0 )
		{
			IOFunctions.printErr( "No interest points available, stopping. Please run Interest Ppint Detection first" );
			return false;
		}

		// choose the first label that is complete if possible
		if ( defaultLabel < 0 || defaultLabel >= labels.length )
		{
			defaultLabel = -1;

			for ( int i = 0; i < labels.length; ++i )
				if ( !labels[ i ].contains( InterestPointTools.warningLabel ) )
				{
					defaultLabel = i;
					break;
				}

			if ( defaultLabel == -1 )
				defaultLabel = 0;
		}

		final GenericDialog gd = new GenericDialog( "Select Interest Point Label" );

		gd.addChoice( "Interest_points" , labels, labels[ defaultLabel ] );
		gd.addCheckbox( "Use_Corresponding interest points", defaultCorresponding );

		gd.addMessage( "" );

		gd.addSlider( "PSF_size_X (px)", 9, 100, defaultPSFSizeX );
		gd.addSlider( "PSF_size_Y (px)", 9, 100, defaultPSFSizeY );
		gd.addSlider( "PSF_size_Z (px)", 9, 100, defaultPSFSizeZ );

		gd.addMessage( " \nNote: PSF size is in local coordinates [px] of the input view.", GUIHelper.mediumstatusfont );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		final String label = InterestPointTools.getSelectedLabel( labels, defaultLabel = gd.getNextChoiceIndex() );
		final boolean corresponding = defaultCorresponding = gd.getNextBoolean();
		int psfSizeX = defaultPSFSizeX = (int)Math.round( gd.getNextNumber() );
		int psfSizeY = defaultPSFSizeY = (int)Math.round( gd.getNextNumber() );
		int psfSizeZ = defaultPSFSizeZ = (int)Math.round( gd.getNextNumber() );

		// enforce odd number
		if ( psfSizeX % 2 == 0 )
			defaultPSFSizeX = ++psfSizeX;

		if ( psfSizeY % 2 == 0 )
			defaultPSFSizeY = ++psfSizeY;

		if ( psfSizeZ % 2 == 0 )
			defaultPSFSizeZ = ++psfSizeZ;

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Selected options for PSF extraction: " );
		IOFunctions.println( "Interest point label: " + label );
		IOFunctions.println( "Using corresponding interest points: " + corresponding );
		IOFunctions.println( "PSF size X (pixels in input image calibration): " + psfSizeX );
		IOFunctions.println( "PSF size Y (pixels in input image calibration): " + psfSizeY );
		IOFunctions.println( "PSF size Z (pixels in input image calibration): " + psfSizeZ );

		int count = 0;

		for ( final ViewId viewId : viewIds )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Extracting PSF for " + Group.pvid( viewId ) + " ... " );

			final PSFExtraction< FloatType > psf = new PSFExtraction< FloatType >( spimData, viewId, label, corresponding, new FloatType(), new long[]{ psfSizeX, psfSizeY, psfSizeZ } );

			if ( psf.hadDetections() )
			{
				++count;
				spimData.getPointSpreadFunctions().addPSF( viewId, new PointSpreadFunction( spimData, viewId, psf.getPSF() ) );
			}
		}

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Extracted " + count + "/" + viewIds.size() + " PSFs." );

		return true;
	}
}