/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.energy;


import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import appeng.api.networking.energy.IEnergyWatcher;
import appeng.api.networking.energy.IEnergyWatcherHost;
import appeng.me.cache.EnergyGridCache;


/**
 * Maintain my interests, and a global watch list, they should always be fully synchronized.
 */
public class EnergyWatcher implements IEnergyWatcher
{

	final EnergyGridCache gsc;
	final IEnergyWatcherHost myObject;
	final HashSet<EnergyThreshold> myInterests = new HashSet<EnergyThreshold>();

	public EnergyWatcher( final EnergyGridCache cache, final IEnergyWatcherHost host )
	{
		this.gsc = cache;
		this.myObject = host;
	}

	public void post( final EnergyGridCache energyGridCache )
	{
		this.myObject.onThresholdPass( energyGridCache );
	}

	public IEnergyWatcherHost getHost()
	{
		return this.myObject;
	}

	@Override
	public int size()
	{
		return this.myInterests.size();
	}

	@Override
	public boolean isEmpty()
	{
		return this.myInterests.isEmpty();
	}

	@Override
	public boolean contains( final Object o )
	{
		return this.myInterests.contains( o );
	}

	@Override
	public Iterator<Double> iterator()
	{
		return new EnergyWatcherIterator( this, this.myInterests.iterator() );
	}

	@Override
	public Object[] toArray()
	{
		return this.myInterests.toArray();
	}

	@Override
	public <T> T[] toArray( final T[] a )
	{
		return this.myInterests.toArray( a );
	}

	@Override
	public boolean add( final Double e )
	{
		if( this.myInterests.contains( e ) )
		{
			return false;
		}

		final EnergyThreshold eh = new EnergyThreshold( e, this );
		return this.gsc.interests.add( eh ) && this.myInterests.add( eh );
	}

	@Override
	public boolean remove( final Object o )
	{
		final EnergyThreshold eh = new EnergyThreshold( (Double) o, this );
		return this.myInterests.remove( eh ) && this.gsc.interests.remove( eh );
	}

	@Override
	public boolean containsAll( final Collection<?> c )
	{
		return this.myInterests.containsAll( c );
	}

	@Override
	public boolean addAll( final Collection<? extends Double> c )
	{
		boolean didChange = false;

		for( final Double o : c )
		{
			didChange = this.add( o ) || didChange;
		}

		return didChange;
	}

	@Override
	public boolean removeAll( final Collection<?> c )
	{
		boolean didSomething = false;
		for( final Object o : c )
		{
			didSomething = this.remove( o ) || didSomething;
		}
		return didSomething;
	}

	@Override
	public boolean retainAll( final Collection<?> c )
	{
		boolean changed = false;
		final Iterator<Double> i = this.iterator();

		while( i.hasNext() )
		{
			if( !c.contains( i.next() ) )
			{
				i.remove();
				changed = true;
			}
		}

		return changed;
	}

	@Override
	public void clear()
	{
		final Iterator<EnergyThreshold> i = this.myInterests.iterator();
		while( i.hasNext() )
		{
			this.gsc.interests.remove( i.next() );
			i.remove();
		}
	}

	class EnergyWatcherIterator implements Iterator<Double>
	{

		final EnergyWatcher watcher;
		final Iterator<EnergyThreshold> interestIterator;
		EnergyThreshold myLast;

		public EnergyWatcherIterator( final EnergyWatcher parent, final Iterator<EnergyThreshold> i )
		{
			this.watcher = parent;
			this.interestIterator = i;
		}

		@Override
		public boolean hasNext()
		{
			return this.interestIterator.hasNext();
		}

		@Override
		public Double next()
		{
			this.myLast = this.interestIterator.next();
			return this.myLast.Limit;
		}

		@Override
		public void remove()
		{
			EnergyWatcher.this.gsc.interests.remove( this.myLast );
			this.interestIterator.remove();
		}
	}
}
