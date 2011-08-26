/************************************************************************
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 *
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 *
 * Use is subject to license terms.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. You can also
 * obtain a copy of the License at http://odftoolkit.org/docs/license.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ************************************************************************/

/*
 * This file is automatically generated.
 * Don't edit manually.
 */    

package org.odftoolkit.odfdom.dom.element.db;

import org.odftoolkit.odfdom.OdfName;
import org.odftoolkit.odfdom.OdfNamespace;
import org.odftoolkit.odfdom.OdfFileDom;
import org.odftoolkit.odfdom.dom.OdfNamespaceNames;
import org.odftoolkit.odfdom.OdfElement;
import org.odftoolkit.odfdom.dom.attribute.db.DbNameAttribute;
import org.odftoolkit.odfdom.dom.attribute.db.DbTitleAttribute;
import org.odftoolkit.odfdom.dom.attribute.db.DbDescriptionAttribute;


/**
 * DOM implementation of OpenDocument element  {@odf.element db:component-collection}.
 *
 */
public abstract class DbComponentCollectionElement extends OdfElement
{        
    public static final OdfName ELEMENT_NAME = OdfName.newName(OdfNamespaceNames.DB, "component-collection" );


	/**
	 * Create the instance of <code>DbComponentCollectionElement</code> 
	 *
	 * @param  ownerDoc     The type is <code>OdfFileDom</code>
	 */
	public DbComponentCollectionElement( OdfFileDom ownerDoc )
	{
		super( ownerDoc, ELEMENT_NAME	);
	}

	/**
	 * Get the element name 
	 *
	 * @return  return   <code>OdfName</code> the name of element {@odf.element db:component-collection}.
	 */
	public OdfName getOdfName()
	{
		return ELEMENT_NAME;
	}

	/**
	 * Initialization of the mandatory attributes of {@link  DbComponentCollectionElement}
	 *
     * @param dbNameAttributeValue  The mandatory attribute {@odf.attribute  db:name}"
     *
	 */
	public void init(String dbNameAttributeValue)
	{
		setDbNameAttribute( dbNameAttributeValue );
	}

	/**
	 * Receives the value of the ODFDOM attribute representation <code>DbNameAttribute</code> , See {@odf.attribute db:name}
	 *
	 * @return - the <code>String</code> , the value or <code>null</code>, if the attribute is not set and no default value defined.
	 */
	public String getDbNameAttribute()
	{
		DbNameAttribute attr = (DbNameAttribute) getOdfAttribute( OdfName.newName( OdfNamespace.newNamespace(OdfNamespaceNames.DB), "name" ) );
		if( attr != null ){
			return String.valueOf( attr.getValue() );
		}
		return null;
	}
		 
	/**
	 * Sets the value of ODFDOM attribute representation <code>DbNameAttribute</code> , See {@odf.attribute db:name}
	 *
	 * @param dbNameValue   The type is <code>String</code>
	 */
	public void setDbNameAttribute( String dbNameValue )
	{
		DbNameAttribute attr =  new DbNameAttribute( (OdfFileDom)this.ownerDocument );
		setOdfAttribute( attr );
		attr.setValue( dbNameValue );
	}


	/**
	 * Receives the value of the ODFDOM attribute representation <code>DbTitleAttribute</code> , See {@odf.attribute db:title}
	 *
	 * @return - the <code>String</code> , the value or <code>null</code>, if the attribute is not set and no default value defined.
	 */
	public String getDbTitleAttribute()
	{
		DbTitleAttribute attr = (DbTitleAttribute) getOdfAttribute( OdfName.newName( OdfNamespace.newNamespace(OdfNamespaceNames.DB), "title" ) );
		if( attr != null ){
			return String.valueOf( attr.getValue() );
		}
		return null;
	}
		 
	/**
	 * Sets the value of ODFDOM attribute representation <code>DbTitleAttribute</code> , See {@odf.attribute db:title}
	 *
	 * @param dbTitleValue   The type is <code>String</code>
	 */
	public void setDbTitleAttribute( String dbTitleValue )
	{
		DbTitleAttribute attr =  new DbTitleAttribute( (OdfFileDom)this.ownerDocument );
		setOdfAttribute( attr );
		attr.setValue( dbTitleValue );
	}


	/**
	 * Receives the value of the ODFDOM attribute representation <code>DbDescriptionAttribute</code> , See {@odf.attribute db:description}
	 *
	 * @return - the <code>String</code> , the value or <code>null</code>, if the attribute is not set and no default value defined.
	 */
	public String getDbDescriptionAttribute()
	{
		DbDescriptionAttribute attr = (DbDescriptionAttribute) getOdfAttribute( OdfName.newName( OdfNamespace.newNamespace(OdfNamespaceNames.DB), "description" ) );
		if( attr != null ){
			return String.valueOf( attr.getValue() );
		}
		return null;
	}
		 
	/**
	 * Sets the value of ODFDOM attribute representation <code>DbDescriptionAttribute</code> , See {@odf.attribute db:description}
	 *
	 * @param dbDescriptionValue   The type is <code>String</code>
	 */
	public void setDbDescriptionAttribute( String dbDescriptionValue )
	{
		DbDescriptionAttribute attr =  new DbDescriptionAttribute( (OdfFileDom)this.ownerDocument );
		setOdfAttribute( attr );
		attr.setValue( dbDescriptionValue );
	}

	/**
	 * Create child element {@odf.element db:component}.
	 *
     * @param dbNameAttributeValue  the <code>String</code> value of <code>DbNameAttribute</code>, see {@odf.attribute  db:name} at specification
	 * @return   return  the element {@odf.element db:component}
	 * DifferentQName 
	 */
    
	public DbComponentElement newDbComponentElement(String dbNameAttributeValue)
	{
		DbComponentElement  dbComponent = ((OdfFileDom)this.ownerDocument).newOdfElement(DbComponentElement.class);
		dbComponent.setDbNameAttribute( dbNameAttributeValue );
		this.appendChild( dbComponent);
		return  dbComponent;      
	}
    
	/**
	 * Create child element {@odf.element db:component-collection}.
	 *
     * @param dbNameAttributeValue  the <code>String</code> value of <code>DbNameAttribute</code>, see {@odf.attribute  db:name} at specification
	 * @return   return  the element {@odf.element db:component-collection}
	 * DifferentQName 
	 */
    
	public DbComponentCollectionElement newDbComponentCollectionElement(String dbNameAttributeValue)
	{
		DbComponentCollectionElement  dbComponentCollection = ((OdfFileDom)this.ownerDocument).newOdfElement(DbComponentCollectionElement.class);
		dbComponentCollection.setDbNameAttribute( dbNameAttributeValue );
		this.appendChild( dbComponentCollection);
		return  dbComponentCollection;      
	}
    
}