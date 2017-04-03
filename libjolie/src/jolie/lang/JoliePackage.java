/*********************************************************************************
 *   Copyright (C) 2006-2014 by Fabrizio Montesi <famontesi@gmail.com>           *
 *                                                                               *
 *   This program is free software; you can redistribute it and/or modify        *
 *   it under the terms of the GNU Library General Public License as             *
 *   published by the Free Software Foundation; either version 2 of the          *
 *   License, or (at your option) any later version.                             *
 *                                                                               *
 *   This program is distributed in the hope that it will be useful,             *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of              *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the               *
 *   GNU General Public License for more details.                                *
 *                                                                               *
 *   You should have received a copy of the GNU Library General Public           *
 *   License along with this program; if not, write to the                       *
 *   Free Software Foundation, Inc.,                                             *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.                   *
 *                                                                               *
 *   For details about the authors of this software, see the AUTHORS file.       *
 *********************************************************************************/

package jolie.lang;

import java.util.Objects;

public class JoliePackage
{
	private final String name;
	private final String root;
	private final String entryPoint;

	public JoliePackage( String name, String root, String entryPoint )
	{
		Objects.requireNonNull( name );
		Objects.requireNonNull( root );

		this.name = name;
		this.root = root;
		this.entryPoint = entryPoint;
	}

	public JoliePackage( String name, String root )
	{
		this( name, root, null );
	}

	public String getName()
	{
		return name;
	}

	public String getRoot()
	{
		return root;
	}

	public String getEntryPoint()
	{
		return entryPoint;
	}

	@Override
	public boolean equals( Object o )
	{
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		JoliePackage aPackage = ( JoliePackage ) o;

		if ( !name.equals( aPackage.name ) ) return false;
		if ( !root.equals( aPackage.root ) ) return false;
		return entryPoint != null ? entryPoint.equals( aPackage.entryPoint ) : aPackage.entryPoint == null;
	}

	@Override
	public int hashCode()
	{
		int result = name.hashCode();
		result = 31 * result + root.hashCode();
		result = 31 * result + ( entryPoint != null ? entryPoint.hashCode() : 0 );
		return result;
	}

	@Override
	public String toString()
	{
		return "JoliePackage{" +
				"name='" + name + '\'' +
				", root='" + root + '\'' +
				", entryPoint='" + entryPoint + '\'' +
				'}';
	}
}
