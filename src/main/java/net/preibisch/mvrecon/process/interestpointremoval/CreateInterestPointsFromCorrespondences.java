package net.preibisch.mvrecon.process.interestpointremoval;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;

public class CreateInterestPointsFromCorrespondences
{
	public static boolean createFor(
			final SpimData2 spimData,
			final Collection< ? extends ViewId > viewIds,
			final CreateFromCorrespondencesParameters params )
	{
		final ViewInterestPoints vip = spimData.getViewInterestPoints();

		for ( final ViewId viewId : viewIds )
		{
			final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId );

			final ViewInterestPointLists vipl = vip.getViewInterestPointLists( viewId );
			final InterestPointList oldIpl = vipl.getInterestPointList( params.getCorrespondingLabel() );

			if ( oldIpl == null )
				continue;

			final ArrayList< InterestPoint > newIPs = new ArrayList<>();

			final HashMap< Integer, InterestPoint > oldIPs = new HashMap<>();
			
			for ( final InterestPoint oldIP : oldIpl.getInterestPointsCopy() )
				oldIPs.put( oldIP.getId(), oldIP );

			final HashSet< Integer > existingPoints = new HashSet<>();

			int id = 0;
			for ( final CorrespondingInterestPoints cp : oldIpl.getCorrespondingInterestPointsCopy() )
			{
				final int oldId = cp.getDetectionId();

				if ( !existingPoints.contains( oldId ) )
				{
					existingPoints.add( oldId );
					final InterestPoint ip = oldIPs.get( oldId );
					newIPs.add( new InterestPoint( id++, ip.getL() ) );
				}
			}

			final InterestPointList newIpl = new InterestPointList(
					oldIpl.getBaseDir(),
					new File(
							oldIpl.getFile().getParentFile(),
							"tpId_" + viewId.getTimePointId() + "_viewSetupId_" + viewId.getViewSetupId() + "." + params.getNewLabel() ) );

			newIpl.setInterestPoints( newIPs );
			newIpl.setCorrespondingInterestPoints( new ArrayList<>() );
			newIpl.setParameters( "correspondences from '" + params.getCorrespondingLabel() + "'" );

			vipl.addInterestPointList( params.getNewLabel(), newIpl );

			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": TP=" + vd.getTimePointId() + " ViewSetup=" + vd.getViewSetupId() + 
					", Detections: " + oldIpl.getInterestPointsCopy().size() + " >>> " + newIpl.getInterestPointsCopy().size() );
		}

		return true;
	}
}
